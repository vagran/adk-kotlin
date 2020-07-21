/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

/** Used for string to enum conversion below. */
private enum class DummyEnum

/** Use it with data class instance copy() method.
 * @param params Map with parameters to override.
 */
fun <T> MutateEntity(copyMethod: KCallable<T>, params: Map<String, Any?>): T
{
    val methodParams = copyMethod.parameters
    val args = HashMap<KParameter, Any?>(methodParams.size)
    for ((name, value) in params) {
        val param = methodParams.find { it.name == name }
            ?: throw Error("Unrecognized parameter: $name")
        var _value = value
        if (value != null) {
            val valCls = value::class
            val paramCls = param.type.jvmErasure
            if (valCls != paramCls) {

                run convert@{
                    /* All numbers are represented as Double in JSON so convert them to other
                     * numeric type if necessary.
                     */
                    if (value is Double) {
                        when (paramCls) {
                            Int::class -> {
                                _value = value.roundToInt()
                                return@convert
                            }
                            Long::class -> {
                                _value = value.roundToLong()
                                return@convert
                            }
                            Float::class -> {
                                _value = value.toFloat()
                                return@convert
                            }
                        }
                    } else if (value is String && paramCls.isSubclassOf(Enum::class)) {
                        /* Cast to any enum class to succeed the cast. */
                        @Suppress("UNCHECKED_CAST")
                        _value = java.lang.Enum.valueOf(paramCls.java as Class<DummyEnum>, value)
                        return@convert
                    }
                    throw Error(
                        "Parameter ${param.name} type mismatch: have $valCls, expected $paramCls")
                }
            }
        }
        args[param] = _value
    }
    return copyMethod.callBy(args)
}
