package io.github.vagran.adk.json.internal

import io.github.vagran.adk.omm.GetDefaultConstructor
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility

typealias ConstructorFunc = () -> Any

fun GetDefaultConstructor(cls: KClass<*>): ConstructorFunc?
{
    val defCtr = try {
        val ctr = GetDefaultConstructor(cls, KVisibility.PUBLIC)
        if (ctr.outerCls == null) {
            ctr
        } else {
            null
        }
    } catch(e: Exception) {
        null
    }
    if (defCtr != null) {
        return { defCtr.Construct(null) }
    } else {
        return null
    }
}
