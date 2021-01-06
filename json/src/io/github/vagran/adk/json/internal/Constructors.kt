/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.internal

import io.github.vagran.adk.omm.GetDefaultConstructor
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility

typealias ConstructorFunc = () -> Any

fun GetDefaultConstructor(cls: KClass<*>, setAccessible: Boolean): ConstructorFunc?
{
    val defCtr = try {
        val ctr = GetDefaultConstructor(cls, KVisibility.PUBLIC, setAccessible)
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
