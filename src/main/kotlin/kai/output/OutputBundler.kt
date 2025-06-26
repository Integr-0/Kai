package net.integr.kai.output

import net.integr.kai.driver.impl.groq.GroqCtx

interface OutputBundler {
    fun onOutput(part: String)
    fun onError(message: String)
    fun onToolCall(toolCall: GroqCtx.Message.ToolCall)
    fun onToolCallResponse(toolCall: GroqCtx.Message.ToolCall, message: String)
    fun onToolCallError(toolCall: GroqCtx.Message.ToolCall, message: String)
}