package net.integr.kai.tool

import net.integr.kai.tool.annotations.Param
import net.integr.kai.tool.annotations.ParamDefault
import net.integr.kai.tool.annotations.ParamEnum
import net.integr.kai.tool.annotations.Tool
import net.integr.kai.tool.data.IntermediateTool
import kotlin.reflect.KCallable
import kotlin.reflect.full.findAnnotation


object ToolLinker {
    fun generateTool(method: KCallable<*>): IntermediateTool {
        val toolAnnotation = method.findAnnotation<Tool>() ?:
            throw IllegalArgumentException("Method must be annotated with @Tool")

        val toolName = toolAnnotation.name
        val toolDescription = toolAnnotation.description

        val requiredTools: MutableList<String> = mutableListOf()

        val parameters = method.parameters.map { param ->
            val paramAnnotation = param.findAnnotation<Param>()
                ?: throw IllegalArgumentException("Parameter must be annotated with @Param")

            val paramName = paramAnnotation.name

            if (paramAnnotation.required) requiredTools += paramName

            val paramType = paramAnnotation.type
            val paramDescription = paramAnnotation.description

            val paramEnum = param.findAnnotation<ParamEnum>()?.values?.toList()
            val paramDefault = param.findAnnotation<ParamDefault>()?.value

            IntermediateTool.Parameter(
                name = paramName,
                type = paramType,
                description = paramDescription,
                enum = paramEnum,
                default = paramDefault
            )
        }.associateBy { it.name }

        return IntermediateTool(
            name = toolName,
            description = toolDescription,
            parameters = parameters,
            required = requiredTools,
            callable = method
        )
    }
}