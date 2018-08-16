package com.ast.adk.json.internal

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility

typealias ConstructorFunc = () -> Any

fun GetDefaultConstructor(cls: KClass<*>): ConstructorFunc?
{
    if (cls.isAbstract || cls.isSealed || cls.objectInstance != null) {
        return null
    }
    for (ctr in cls.constructors) {
        val defCtr = CheckConstructor(ctr)
        if (defCtr != null) {
            if (ctr.visibility != KVisibility.PUBLIC) {
                return null
            }
            return defCtr
        }
    }
    return null
}

private fun CheckConstructor(ctr: KFunction<*>): ConstructorFunc?
{
    for (paramIdx in 0 until ctr.parameters.size) {
        val param = ctr.parameters[paramIdx]
        if (!param.isOptional) {
            return null
        }
    }
    return { ctr.callBy(emptyMap()) as Any }
}
