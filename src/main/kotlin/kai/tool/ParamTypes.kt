package net.integr.kai.tool

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

object ParamTypes {
    const val STRING = "string"
    const val INT = "int"
    const val FLOAT = "float"
    const val BOOLEAN = "boolean"
    const val OBJECT = "object"
    const val ARRAY = "array"

    fun isValidType(type: String): Boolean {
        return type in listOf(STRING, INT, FLOAT, BOOLEAN, OBJECT, ARRAY)
    }

    fun parsePrimitive(primitive: JsonPrimitive, type: String): Any? {
        return when (type) {
            STRING -> primitive.content
            INT -> primitive.intOrNull ?: primitive.content.toIntOrNull()
            FLOAT -> primitive.floatOrNull ?: primitive.content.toFloatOrNull()
            BOOLEAN -> primitive.booleanOrNull ?: primitive.content.toBooleanStrictOrNull()
            else -> null
        }
    }
}