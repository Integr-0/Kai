package net.integr.kai.driver.impl.groq

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.integr.kai.driver.ModelDriver
import net.integr.kai.json.JsonHolder
import net.integr.kai.output.OutputBundler
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.data.IntermediateTool
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class GroqDriver(val model: GroqModel, val toolChoice: String, val maxCompletionTokens: Int, systemMessage: String?, intermediateTools: List<IntermediateTool>, val apiKey: String) : ModelDriver {
    class Builder {
        private lateinit var model: GroqModel
        private var toolChoice: String = "auto"
        private var maxCompletionTokens: Int = 4096
        private var systemMessage: String? = null
        private var tools: List<IntermediateTool> = emptyList()
        private lateinit var apiKey: String

        fun model(model: GroqModel) = apply { this.model = model }
        fun toolChoice(toolChoice: String) = apply { this.toolChoice = toolChoice }
        fun maxCompletionTokens(maxCompletionTokens: Int) = apply { this.maxCompletionTokens = maxCompletionTokens }
        fun systemMessage(systemMessage: String) = apply { this.systemMessage = systemMessage }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun withTool(tool: IntermediateTool) = apply {
            tools = if (tools.isEmpty()) {
                listOf(tool)
            } else {
                tools + tool
            }
        }

        fun build() = GroqDriver(model, toolChoice, maxCompletionTokens, systemMessage, tools, apiKey)
    }

    val url = "https://api.groq.com/openai/v1/chat/completions"

    private val messages = mutableListOf<GroqCtx.Message>()
    private val tools = mutableMapOf<IntermediateTool, GroqCtx.Tool>()

    private val httpClient = HttpClient.newHttpClient()

    init {
        systemMessage?.let {
            messages.add(GroqCtx.Message(role = "system", content = it))
        }

        intermediateTools.forEach {
            tools[it] = GroqCtx.Tool(
                type = "function",
                function = GroqCtx.Tool.Function(
                    name = it.name,
                    description = it.description,
                    parameters = GroqCtx.Tool.Function.Parameters(
                        type = "object",
                        properties = it.parameters.mapValues { (name, param) ->
                            GroqCtx.Tool.Function.Parameters.Property(
                                type = param.type,
                                description = param.description,
                                enum = param.enum,
                            )
                        },
                        required = it.required
                    )
                )
            )
        }
    }

    private fun send(output: OutputBundler, provideTools: Boolean = true) {
        val builtReq = GroqCtx(model.modelName,
            messages = messages,
            tools = if (provideTools) tools.values else emptyList(),
            toolChoice = toolChoice,
            maxCompletionTokens = maxCompletionTokens,
            stream = true
        )

        val json = builtReq.toJson()

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val reader = BufferedReader(InputStreamReader(response.body()))
        handleStreamResponse(reader, output)
    }

    private fun handleStreamResponse(reader: BufferedReader, output: OutputBundler) {
        var role: String? = null
        var finishedMessageContent: String? = ""
        var finishReason: String? = null
        val toolCallsResponses: MutableMap<GroqCtx.Message.ToolCall, GroqCtx.Message> = mutableMapOf()

        for (line in reader.lines()) {
            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") {
                    break
                }

                try {
                    val json = JsonHolder.JSON.decodeFromString<JsonObject>(data)
                    val choices = json["choices"]?.jsonArray ?: continue
                    val delta = choices[0].jsonObject["delta"]

                    val toolCallsRead = delta?.jsonObject["tool_calls"]?.jsonArray
                    val finishReasonRead = choices[0].jsonObject["finish_reason"]?.jsonPrimitive?.content
                    val contentRead = delta?.jsonObject["content"]?.jsonPrimitive?.content
                    val roleRead = delta?.jsonObject["role"]?.jsonPrimitive?.content

                    if (roleRead != null) role = roleRead
                    if (contentRead != null && contentRead != "null") {
                        finishedMessageContent += contentRead
                        output.onOutput(contentRead)
                    }

                    if (finishReasonRead != null) finishReason = finishReasonRead

                    toolCallsRead?.forEach { toolCallElement ->
                        val toolCall = JsonHolder.JSON.decodeFromJsonElement<GroqCtx.Message.ToolCall>(toolCallElement)

                        val toolName = toolCall.function.name
                        val tool = determineToolCall(toolName) ?: throw IllegalStateException("Tool $toolName not found")

                        val params = toolCall.function.arguments
                        val paramMap = JsonHolder.JSON.decodeFromString<JsonObject>(params)
                        val toolCallId = toolCall.id

                        output.onToolCall(toolCall)

                        val parsedParams = paramMap.mapValues { (key, value) ->
                            if (key !in tool.parameters) throw IllegalArgumentException("Parameter $key not found in tool ${tool.name}")

                            val paramType = tool.parameters[key]!!.type
                            if (!ParamTypes.isValidType(paramType)) throw IllegalArgumentException("Invalid type $paramType for parameter $key in tool ${tool.name}")

                            ParamTypes.parsePrimitive(value.jsonPrimitive, paramType)
                        }

                        val toolResponse = tool.invoke(parsedParams.values.toTypedArray())
                        if (toolResponse != null) {
                            toolCallsResponses[toolCall] = GroqCtx.Message(role = "tool", content = "$toolResponse", toolCallId = toolCallId, name = toolName)
                            output.onToolCallResponse(toolCall, toolResponse.toString())
                        } else output.onToolCallError(toolCall,"Tool returned null response.")
                    }

                    if (role != "assistant") throw IllegalStateException("Expected assistant response, got: $role")

                } catch (e: Exception) {
                    error("\n[Error parsing chunk] ${e.message}")
                }
            }
        }

        val newAssistantMessage = GroqCtx.Message(
            role = role ?: "assistant",
            content = finishedMessageContent ?: "",
            toolCalls = toolCallsResponses.keys.toMutableList(),
        )

        messages.add(newAssistantMessage)

        if (finishReason == "stop") {
            println()
            return
        }

        if (finishReason == "tool_calls") {
            messages.addAll(toolCallsResponses.values)

            send(output)
        }
    }

    private fun determineToolCall(toolName: String): IntermediateTool? {
        return tools.entries.firstOrNull { it.value.function.name == toolName }?.key
    }

    override fun query(query: String, output: OutputBundler) {
        val userMessage = GroqCtx.Message(role = "user", content = query)
        messages.add(userMessage)

        send(output)
    }
}