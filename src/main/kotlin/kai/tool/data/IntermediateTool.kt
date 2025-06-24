package net.integr.kai.tool.data

import kotlin.reflect.KCallable

data class IntermediateTool(
    val name: String,
    val description: String,
    val parameters: Map<String, Parameter>,
    val required: List<String>,
    val callable: KCallable<*>
) {
    data class Parameter(
        val name: String,
        val type: String,
        val description: String,
        val enum: List<String>? = null,
        val default: String? = null
    )

    fun invoke(args: Array<Any?>): Any? {
        return callable.call(*args)
    }
}