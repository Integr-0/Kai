package net.integr.kai.driver.impl.groq

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import net.integr.kai.driver.ModelDriver
import net.integr.kai.json.JsonHolder
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.data.IntermediateTool
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

    private fun send(provideTools: Boolean = true) {
        val builtReq = GroqCtx(model.modelName,
            messages = messages,
            tools = if (provideTools) tools.values else emptyList(),
            toolChoice = toolChoice,
            maxCompletionTokens = maxCompletionTokens
        )

        val json = builtReq.toJson()

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        handleResponse(response.body())
    }

    private fun handleResponse(response: String) {
        val json = JsonHolder.JSON.decodeFromString<JsonObject>(response)
        val choices = json["choices"]?.jsonArray ?: return
        val firstChoice = choices.firstOrNull() ?: return

        val responseObject = JsonHolder.JSON.decodeFromJsonElement<GroqResponse>(firstChoice)
        if (responseObject.message.role != "assistant") throw IllegalStateException("Expected assistant response, got: ${responseObject.message.role}")

        messages.add(responseObject.message)

        if (responseObject.message.toolCalls == null || responseObject.finishReason == "stop") {
            println("[Response]: ${responseObject.message.content}")
            return
        }

        responseObject.message.toolCalls.forEach { toolCall ->
            val toolName = toolCall.function.name
            val tool = determineToolCall(toolName) ?: throw IllegalStateException("Tool $toolName not found")

            val params = toolCall.function.arguments
            val paramMap = JsonHolder.JSON.decodeFromString<JsonObject>(params)
            val toolCallId = toolCall.id

            println("[Tool call ($toolCallId)]: $toolName with params: $paramMap")

            val parsedParams = paramMap.mapValues { (key, value) ->
                if (key !in tool.parameters) throw IllegalArgumentException("Parameter $key not found in tool ${tool.name}")

                val paramType = tool.parameters[key]!!.type
                if (!ParamTypes.isValidType(paramType)) throw IllegalArgumentException("Invalid type $paramType for parameter $key in tool ${tool.name}")

                ParamTypes.parsePrimitive(value.jsonPrimitive, paramType)
            }

            val toolResponse = tool.invoke(parsedParams.values.toTypedArray())
            if (toolResponse != null) {
                messages.add(GroqCtx.Message(role = "tool", content = "$toolResponse", toolCallId = toolCallId, name = toolName))
                messages.add(GroqCtx.Message(role = "assistant", content = "Tool executed!"))
                messages.add(GroqCtx.Message(role = "user", content = "Continue!"))

                println("[Tool $toolName ($toolCallId) response]: $toolResponse")
                send()
            } else {
                println("[Tool $toolName ($toolCallId) returned null response]")
            }
        }
    }

    private fun determineToolCall(toolName: String): IntermediateTool? {
        return tools.entries.firstOrNull { it.value.function.name == toolName }?.key
    }

    override fun query(query: String) {
        val userMessage = GroqCtx.Message(role = "user", content = query)
        messages.add(userMessage)

        send()
    }
}