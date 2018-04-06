package com.ast.adk.async

interface Awaitable<T> {

    /** Asynchronous wait for result. No any guarantee about continuation context. */
    suspend fun Await(): T

    /** Asynchronous wait for result. Continuation is bound to the specified context. */
    suspend fun Await(ctx: Context): T
    {
        return ctx.ResumeIn({Await()})
    }

    /** Asynchronous wait for result. Continuation is bound to the current context if any.
     * Arbitrary context if no current context set.
     */
    suspend fun AwaitCurrent(): T
    {
        val ctx = Context.current
        if (ctx == null) {
            return Await()
        } else {
            return Await(ctx)
        }
    }
}