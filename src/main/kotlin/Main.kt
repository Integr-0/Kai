package net.integr

import net.integr.kai.driver.impl.groq.data.GroqCtx
import net.integr.kai.driver.impl.groq.GroqDriver
import net.integr.kai.driver.impl.groq.data.GroqModel
import net.integr.kai.output.OutputBundler
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.ToolLinker
import net.integr.kai.tool.annotations.Param
import net.integr.kai.tool.annotations.ParamDefault
import net.integr.kai.tool.annotations.ParamEnum
import net.integr.kai.tool.annotations.Tool

fun main() {
    val getWeatherTool = ToolLinker.generateTool(::getWeather)
    val reverseStringTool = ToolLinker.generateTool(::reverseString)

    val outBundler = object : OutputBundler {
        override fun onBeginOutput() {
            println("[OUTPUT]:")
        }

        override fun onOutput(part: String) {
            print(part)
        }

        override fun onEndOutput() {
            println()
        }

        override fun onBeginReason() {
            println("[REASONING]:")
        }

        override fun onReason(part: String) {
            print(part)
        }

        override fun onEndReason() {
            println()
        }

        override fun onError(message: String) {
            error("[ERROR]: $message\n")
        }

        override fun onToolCall(toolCall: GroqCtx.Message.ToolCall) {
            println("\n[TOOL CALL]: Id ${toolCall.id} on ${toolCall.function.name} with params: ${toolCall.function.arguments}")
        }

        override fun onToolCallResponse(toolCall: GroqCtx.Message.ToolCall, message: String) {
            println("[TOOL RESPONSE]: Id ${toolCall.id} on ${toolCall.function.name} returned: $message\n")
        }

        override fun onToolCallError(toolCall: GroqCtx.Message.ToolCall, message: String) {
            error("[TOOL ERROR]: $message\n")
        }
    }

    val ai = GroqDriver.Builder()
        .model(GroqModel.QWEN_QWEN3_32B)
        .systemMessage("Once you have what you need, dont call anymore tools/functions and answer the user.")
        .apiKey(System.getenv("GROQ_API_KEY") ?: throw IllegalStateException("GROQ_API_KEY environment variable is not set"))
        .allowRecursiveToolCalls()
        .useReasoning()
        .withTool(getWeatherTool)
        .withTool(reverseStringTool)
        .build()

    var currentQuery: String

    while (true) {
        print("[QUERY]: ")
        currentQuery = readLine() ?: break
        ai.query(currentQuery, outBundler)
    }
}

@Tool("get_weather", "Get the general current weather for a location, returns the temperature")
fun getWeather(
    @Param("location", ParamTypes.STRING, "the general location to inquire about") location: String,
    @Param("unit", ParamTypes.STRING, "the unit for the result") @ParamEnum("fahrenheit", "celsius") @ParamDefault("fahrenheit") unit: String
): String {
    return "75 degrees $unit"
}

@Tool("reverse_string", "Use this tool to reverse a string, returns the reversed string.")
fun reverseString(
    @Param("input", ParamTypes.STRING, "Put the string to reverse in this parameter.") input: String
): String {
    return input.reversed()
}