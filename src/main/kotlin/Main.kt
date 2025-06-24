package net.integr

import net.integr.kai.driver.impl.groq.GroqDriver
import net.integr.kai.driver.impl.groq.GroqModel
import net.integr.kai.tool.ParamTypes
import net.integr.kai.tool.ToolLinker
import net.integr.kai.tool.annotations.Param
import net.integr.kai.tool.annotations.ParamDefault
import net.integr.kai.tool.annotations.ParamEnum
import net.integr.kai.tool.annotations.Tool

fun main() {
    val getWeatherTool = ToolLinker.generateTool(::getWeather)
    val reverseStringTool = ToolLinker.generateTool(::reverseString)

    val ai = GroqDriver.Builder().model(GroqModel.DEEPSEEK_LLAMA_70B)
        .systemMessage("You are a helpful assistant. Avoid calling the same tool repeatedly with inputs that undo prior operations. After tool calls, provide a final, concise answer. ")
        .apiKey(System.getenv("GROQ_API_KEY") ?: throw IllegalStateException("GROQ_API_KEY environment variable is not set"))
        .withTool(getWeatherTool)
        .withTool(reverseStringTool)
        .build()

    ai.query("Reverse 'Hello' and then the result again. Use your tools on every reversal for this job.")

}

@Tool("get_weather", "Get the general current weather for a location")
fun getWeather(
    @Param("location", ParamTypes.STRING, "the location to inquire about very general") location: String,
    @Param("unit", ParamTypes.STRING, "the unit for the result") @ParamEnum("fahrenheit", "celsius") @ParamDefault("fahrenheit") unit: String
): String {
    return "The current weather in $location is 75 degrees $unit."
}

@Tool("reverse_string", "reverse a string")
fun reverseString(
    @Param("input", ParamTypes.STRING, "The string to reverse") input: String
): String {
    return input.reversed()
}