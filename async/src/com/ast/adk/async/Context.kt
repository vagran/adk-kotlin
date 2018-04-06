package com.ast.adk.async

import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

/** Execution context which receives messages. */
interface Context {

    companion object {
        /** Bound context for the current thread. */
        var current: Context?
            get() = contextImpl.get()
            set(value) = contextImpl.set(value)

        private val contextImpl: ThreadLocal<Context?> = ThreadLocal()
    }

    /** Submit a message. Either message.Invoke() or message.Reject() will be called inside this
     * context.
     */
    fun Submit(message: Message)

    /** Wrap the provided function so that it continues in this context. */
    suspend fun <T> ResumeIn(func: suspend () -> T): T
    {
        var funcError: Throwable? = null
        var funcResult: T? = null
        try {
            funcResult = func()
        } catch (_error: Throwable) {
            funcError = _error;
        }
        return suspendCoroutine {
            cont ->

            Submit(object: Message {
                override fun Invoke()
                {
                    if (funcError != null) {
                        cont.resumeWithException(funcError)
                    } else {
                        cont.resume(funcResult!!)
                    }
                }

                override fun Reject(error: Throwable)
                {
                    cont.resumeWithException(error)
                }
            })
        }
    }

    /** Continues execution in this context. */
    suspend fun ResumeIn()
    {
        suspendCoroutine {
            cont: Continuation<Unit> ->

            Submit(object: Message {
                override fun Invoke()
                {
                    cont.resume(Unit)
                }

                override fun Reject(error: Throwable)
                {
                    cont.resumeWithException(error)
                }
            })
        }
    }
}
