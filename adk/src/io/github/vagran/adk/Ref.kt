/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


interface Ref<out T: Any> {
    val obj: T

    fun AddRef(referent: Any): Ref<T>
    /** @return True if last reference released. */
    fun Release(): Boolean
    fun WeakRef(): WeakRef<out T>
}

interface WeakRef<T: Any> {
    /** @return Locked reference, null if already destructed. */
    fun Lock(referent: Any): Ref<T>?
}

@ExperimentalContracts
inline fun <T: Any, R> Ref<T>.use(block: (ref: Ref<T>) -> R): R
{
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block(this)
    } finally {
        Release()
    }
}
