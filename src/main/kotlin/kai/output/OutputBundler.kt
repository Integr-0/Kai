package net.integr.kai.output

import net.integr.kai.driver.impl.groq.data.GroqCtx

interface OutputBundler {
    fun onBeginOutput()
    fun onOutput(part: String)
    fun onEndOutput()
    fun onBeginReason()
    fun onReason(part: String)
    fun onEndReason()
    fun onError(message: String)
    fun onToolCall(toolCall: GroqCtx.Message.ToolCall)
    fun onToolCallResponse(toolCall: GroqCtx.Message.ToolCall, message: String)
    fun onToolCallError(toolCall: GroqCtx.Message.ToolCall, message: String)
}