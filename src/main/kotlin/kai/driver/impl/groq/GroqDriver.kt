package net.integr.kai.driver.impl.groq

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.integr.kai.driver.ModelDriver
import net.integr.kai.driver.impl.groq.data.GroqCtx
import net.integr.kai.driver.impl.groq.data.GroqModel
import net.integr.kai.driver.impl.groq.tool.GroqToolHandler
import net.integr.kai.json.JsonHolder
import net.integr.kai.output.GenericStatus
import net.integr.kai.output.OutputBundler
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.data.IntermediateTool
import net.integr.kai.web.HttpConnector
import java.io.BufferedReader
import java.io.InputStreamReader

class GroqDriver private constructor(val model: GroqModel, val toolChoice: String, val maxCompletionTokens: Int, systemMessage: String?, intermediateTools: List<IntermediateTool>, val apiKey: String, val allowRecursiveToolCalls: Boolean, val useReasoning: Boolean) : ModelDriver {
    class Builder {
        private lateinit var model: GroqModel
        private var toolChoice: String = "auto"
        private var maxCompletionTokens: Int = 4096
        private var systemMessage: String? = null
        private var tools: List<IntermediateTool> = emptyList()
        private lateinit var apiKey: String
        private var allowRecursiveToolCalls: Boolean = false
        private var useReasoning: Boolean = false

        fun model(model: GroqModel) = apply { this.model = model }
        fun toolChoice(toolChoice: String) = apply { this.toolChoice = toolChoice }
        fun maxCompletionTokens(maxCompletionTokens: Int) = apply { this.maxCompletionTokens = maxCompletionTokens }
        fun systemMessage(systemMessage: String) = apply { this.systemMessage = systemMessage }
        fun allowRecursiveToolCalls() = apply { allowRecursiveToolCalls = true }
        fun useReasoning() = apply { useReasoning = true }
        fun apiKey(apiKey: String) = apply { this.apiKey = apiKey }
        fun withTool(tool: IntermediateTool) = apply {
            tools = if (tools.isEmpty()) {
                listOf(tool)
            } else {
                tools + tool
            }
        }

        fun build() = GroqDriver(model, toolChoice, maxCompletionTokens, systemMessage, tools, apiKey, allowRecursiveToolCalls, useReasoning)
    }

    val url = "https://api.groq.com/openai/v1/chat/completions"

    private val messages = mutableListOf<GroqCtx.Message>()
    private val toolHandler = GroqToolHandler()

    init {
        systemMessage?.let {
            messages.add(GroqCtx.Message(role = "system", content = it))
        }

        toolHandler.setupTools(intermediateTools)
    }

    private fun send(output: OutputBundler, provideTools: Boolean = true) {
        val builtReq = GroqCtx(
            model.modelName,
            messages = messages,
            tools = if (provideTools) toolHandler.groqTools else emptyList(),
            toolChoice = toolChoice,
            maxCompletionTokens = maxCompletionTokens,
            stream = true,
            reasoningEffort = if (useReasoning) "default" else null
        )

        val json = builtReq.toJson()

        val request = HttpConnector.newJsonRequestWithBearer(url, apiKey, json)
        val response = HttpConnector.sendAndGetStream(request)

        val reader = BufferedReader(InputStreamReader(response))
        handleStreamResponse(reader, output)
    }

    private fun handleStreamResponse(reader: BufferedReader, output: OutputBundler) {
        var role: String? = null
        var finishedMessageContent: String? = ""
        var finishedReasoningContent: String? = null
        var finishReason: String? = null
        val toolCallsResponses: MutableMap<GroqCtx.Message.ToolCall, GroqCtx.Message> = mutableMapOf()

        var reasoningStatus = if (useReasoning) GenericStatus.WAITING else GenericStatus.FINISHED
        var contentStatus = GenericStatus.WAITING

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
                    val reasoningRead = delta?.jsonObject["reasoning"]?.jsonPrimitive?.content
                    val finishReasonRead = choices[0].jsonObject["finish_reason"]?.jsonPrimitive?.content
                    val contentRead = delta?.jsonObject["content"]?.jsonPrimitive?.content
                    val roleRead = delta?.jsonObject["role"]?.jsonPrimitive?.content

                    if (roleRead != null) role = roleRead

                    if (reasoningRead != null && reasoningRead != "null" && reasoningRead.isNotBlank()) {
                        reasoningStatus = reasoningStatus.moveIfWaiting { output.onBeginReason() }

                        finishedReasoningContent += reasoningRead
                        output.onReason(reasoningRead)
                        continue
                    }

                    if (contentRead != null && contentRead != "null" && contentRead.isNotBlank()) {
                        reasoningStatus = reasoningStatus.moveIfStarted { output.onEndReason() }
                        contentStatus = contentStatus.moveIfWaiting { output.onBeginOutput() }

                        finishedMessageContent += contentRead
                        output.onOutput(contentRead)

                        continue
                    }



                    if (finishReasonRead != null) {
                        contentStatus = contentStatus.moveIfStarted { output.onEndOutput() }

                        finishReason = finishReasonRead
                    }

                    toolCallsRead?.forEach { toolCallElement ->
                        val toolCall = JsonHolder.JSON.decodeFromJsonElement<GroqCtx.Message.ToolCall>(toolCallElement)

                        toolHandler.handleToolCall(toolCall, output, toolCallsResponses)
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

            send(output, allowRecursiveToolCalls)
        }
    }


    override fun query(query: String, output: OutputBundler) {
        val userMessage = GroqCtx.Message(role = "user", content = query)
        messages.add(userMessage)

        send(output)
    }
}