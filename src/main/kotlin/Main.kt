package net.integr

import net.integr.kai.driver.impl.groq.GroqCtx
import net.integr.kai.driver.impl.groq.GroqDriver
import net.integr.kai.driver.impl.groq.GroqModel
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

    val ai = GroqDriver.Builder().model(GroqModel.QWEN_QWEN3_32B)
        .systemMessage("Once you have what you need, dont call anymore tools/functions and answer the user.")
        .apiKey(System.getenv("GROQ_API_KEY") ?: throw IllegalStateException("GROQ_API_KEY environment variable is not set"))
        .withTool(getWeatherTool)
        .withTool(reverseStringTool)
        .build()

    var currentQuery: String

    val outBundler = object : OutputBundler {
        override fun onOutput(part: String) {
            print(part)
        }

        override fun onError(message: String) {
            error("[ERROR]: $message")
        }

        override fun onToolCall(toolCall: GroqCtx.Message.ToolCall) {
            println("[TOOL CALL]: Id ${toolCall.id} on ${toolCall.function.name} with params: ${toolCall.function.arguments}")
        }

        override fun onToolCallResponse(toolCall: GroqCtx.Message.ToolCall, message: String) {
            println("[TOOL RESPONSE]: Id ${toolCall.id} on ${toolCall.function.name} returned: $message")
        }

        override fun onToolCallError(toolCall: GroqCtx.Message.ToolCall, message: String) {
            error("[TOOL ERROR]: $message")
        }
    }

    while (true) {
        print("[QUERY]: ")
        currentQuery = readLine() ?: break
        ai.query(currentQuery, outBundler)
    }
}

@Tool("get_weather", "Get the general current weather for a location, returns the temperature")
fun getWeather(
    @Param("location", ParamTypes.STRING, "the location to inquire about very general") location: String,
    @Param("unit", ParamTypes.STRING, "the unit for the result") @ParamEnum("fahrenheit", "celsius") @ParamDefault("fahrenheit") unit: String
): String {
    return "The current weather in $location is 75 degrees $unit."
}

@Tool("reverse_string", "Use this tool to reverse a string, returns the reversed string.")
fun reverseString(
    @Param("input", ParamTypes.STRING, "Put the string to reverse in this parameter.") input: String
): String {
    return input.reversed()
}