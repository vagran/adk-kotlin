/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

import java.util.*

/** Asynchronous lock. */
class AsyncLock {

    fun Acquire(): Deferred<Unit>
    {
        synchronized(queue) {
            if (!isLocked) {
                isLocked = true
                return Deferred.Unit()
            }
            val def = Deferred.Create<Unit>()
            queue.addLast(def)
            return def
        }
    }

    fun Release()
    {
        val def = synchronized(queue) {
            if (!isLocked) {
                throw Error("Not locked")
            }
            val def = queue.pollFirst()
            if (def == null) {
                isLocked = false
            }
            def
        }
        def?.SetResult(Unit)
    }

    fun <T> Synchronized(block: suspend () -> T): Deferred<T>
    {
        val result = Deferred.Create<T>()
        Acquire().Subscribe {
            _, error ->
            if (error != null) {
                result.SetError(error)
                return@Subscribe
            }
            Deferred.ForFunc(block).Subscribe {
                resultValue, _error ->
                Release()
                if (_error != null) {
                    result.SetError(_error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    result.SetResult(resultValue as T)
                }
            }
        }
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val queue = ArrayDeque<Deferred<Unit>>()
    private var isLocked = false
}
