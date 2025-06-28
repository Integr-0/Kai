package net.integr.kai.driver.impl.groq.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import net.integr.kai.json.JsonHolder

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class GroqCtx(
    val model: String,
    val messages: List<Message>,
    val tools: Collection<Tool> = emptyList(),
    val maxCompletionTokens: Int,
    val toolChoice: String,
    val stream: Boolean,
    val reasoningEffort: String? = null,
) {
    @Serializable
    data class Tool(val type: String, val function: Function) {
        @Serializable
        data class Function(val name: String, val description: String, val parameters: Parameters) {
            @Serializable
            data class Parameters(val properties: Map<String, Property>, val required: List<String>, val type: String = "object") {
                @Serializable
                data class Property(val type: String, val description: String, val enum: List<String>? = null)
            }
        }
    }

    @Serializable
    @JsonIgnoreUnknownKeys
    data class Message(
        val role: String,
        val content: String? = null,
        val toolCalls: List<ToolCall>? = null,
        val name: String? = null,
        val toolCallId: String? = null
    ) {
        @Serializable
        @JsonIgnoreUnknownKeys
        data class ToolCall(
            val id: String,
            val type: String,
            val function: FunctionCall
        ) {
            @Serializable
            @JsonIgnoreUnknownKeys
            data class FunctionCall(
                val name: String,
                val arguments: String
            )
        }
    }

    fun toJson(): String {
        return JsonHolder.JSON.encodeToString(this)
    }
}