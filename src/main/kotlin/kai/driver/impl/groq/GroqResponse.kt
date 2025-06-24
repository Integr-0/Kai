package net.integr.kai.driver.impl.groq

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlinx.serialization.json.JsonNames

@Serializable
@JsonIgnoreUnknownKeys
@OptIn(ExperimentalSerializationApi::class)
data class GroqResponse(
    val index: Int,
    val message: GroqCtx.Message,
    val logprobs: String?,
    val finishReason: String,
)