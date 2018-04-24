package com.ast.adk.async

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine

/**
 * Once submitted to the target context the task execution begins.
 */
class Task<T>: Message, Awaitable<T?> {

    /** Task result provided by the task handler when complete. */
    val result: Deferred<T> = Deferred.Create()

    companion object {
        fun <T> Create(handler: () -> T): Task<T>
        {
            return Task(handler)
        }


        // Different name to overcome current type inference problem in Kotlin. Can be made the
        // same when fixed.
        fun <T> CreateDef(handler: suspend () -> T): Task<T>
        {
            return Task(handler)
        }
    }

    /** @param handler Handler which is invoked in the target context and provides task result. */
    constructor(handler: () -> T)
    {
        this.handler = {
            try {
                result.SetResult(handler())
            } catch (e: Throwable) {
                result.SetError(e)
            }
        }
    }

    /** @param handler Handler which is invoked in the target context and provides task result. */
    constructor(handler: suspend () -> T)
    {
        this.handler = {
            handler.createCoroutine(object: Continuation<T> {
                override val context: CoroutineContext = EmptyCoroutineContext

                override fun resume(value: T)
                {
                    result.SetResult(value)
                }

                override fun resumeWithException(exception: Throwable)
                {
                    result.SetError(exception)
                }
            }).resume(Unit)
        }
    }

    override fun Invoke()
    {
        handler()
    }

    override fun Reject(error: Throwable)
    {
        result.SetError(error)
    }

    fun Submit(ctx: Context): Task<T>
    {
        ctx.Submit(this)
        return this
    }

    override suspend fun Await(): T?
    {
        return result.Await()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val handler: () -> Unit
}
