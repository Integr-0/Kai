package net.integr.kai.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
@OptIn(ExperimentalSerializationApi::class)
object JsonHolder {
    val JSON = Json {
         namingStrategy = JsonNamingStrategy.SnakeCase
    }
}