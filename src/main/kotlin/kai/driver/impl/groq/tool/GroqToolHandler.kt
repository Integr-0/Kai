package net.integr.kai.driver.impl.groq.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.integr.kai.driver.impl.groq.data.GroqCtx
import net.integr.kai.json.JsonHolder
import net.integr.kai.output.OutputBundler
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.data.IntermediateTool

class GroqToolHandler {
    val groqTools
        get() = toolsMap.values

    val toolsMap: MutableMap<IntermediateTool, GroqCtx.Tool> = mutableMapOf()

    fun setupTools(intermediateTools: List<IntermediateTool>) {
        groqTools.clear()

        intermediateTools.forEach {
            toolsMap[it] = GroqCtx.Tool(
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

    private fun determineToolCall(toolName: String): IntermediateTool? {
        return toolsMap.entries.firstOrNull { it.value.function.name == toolName }?.key
    }


    fun handleToolCall(toolCall: GroqCtx.Message.ToolCall, output: OutputBundler, toolCallsResponses: MutableMap<GroqCtx.Message.ToolCall, GroqCtx.Message>) {
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
}