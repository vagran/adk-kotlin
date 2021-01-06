/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

interface Awaitable<T> {

    /** Asynchronous wait for result. No any guarantee about continuation context. */
    suspend fun Await(): T

    /** Asynchronous wait for result. Continuation is bound to the specified context. */
    suspend fun Await(ctx: Context): T
    {
        return ctx.ResumeIn { Await() }
    }

    /** Asynchronous wait for result. Continuation is bound to the current context if any.
     * Error is thrown if no current context set.
     */
    suspend fun AwaitCurrent(): T
    {
        val ctx = Context.current
        if (ctx == null) {
            throw Error("No current context")
        } else {
            return Await(ctx)
        }
    }
}
