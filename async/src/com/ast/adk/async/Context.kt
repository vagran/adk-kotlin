package com.ast.adk.async

import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
            funcError = _error
        }
        return suspendCoroutineUninterceptedOrReturn {
            cont ->

            Submit(object: Message {
                @Suppress("UNCHECKED_CAST")
                override fun Invoke()
                {
                    if (funcError != null) {
                        cont.resumeWithException(funcError)
                    } else {
                        cont.resume(funcResult as T)
                    }
                }

                override fun Reject(error: Throwable)
                {
                    cont.resumeWithException(error)
                }
            })

            COROUTINE_SUSPENDED
        }
    }

    /** Continues execution in this context. */
    suspend fun ResumeIn()
    {
        suspendCoroutineUninterceptedOrReturn {
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

            COROUTINE_SUSPENDED
        }
    }

    /** Wrap the provided function so that the call is forwarded to the context. Result is ignored
     * as well as any exception. Use Task when the result is needed.
     */
    fun Wrap(func: () -> Any?): () -> Unit
    {
        return {
            Submit(object: Message {
                override fun Invoke() {
                    func()
                }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    fun <TArg1> Wrap(func: (TArg1) -> Any?): (TArg1) -> Unit
    {
        return {
            arg1 ->
            Submit(object: Message {
                override fun Invoke() { func(arg1) }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    fun <TArg1, TArg2> Wrap(func: (TArg1, TArg2) -> Any?): (TArg1, TArg2) -> Unit
    {
        return {
            arg1, arg2 ->
            Submit(object: Message {
                override fun Invoke() { func(arg1, arg2) }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    fun <TArg1, TArg2, TArg3> Wrap(func: (TArg1, TArg2, TArg3) -> Any?):
        (TArg1, TArg2, TArg3) -> Unit
    {
        return {
            arg1, arg2, arg3 ->
            Submit(object: Message {
                override fun Invoke() { func(arg1, arg2, arg3) }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    fun <TArg1, TArg2, TArg3, TArg4> Wrap(func: (TArg1, TArg2, TArg3, TArg4) -> Any?):
        (TArg1, TArg2, TArg3, TArg4) -> Unit
    {
        return {
            arg1, arg2, arg3, arg4 ->
            Submit(object: Message {
                override fun Invoke() { func(arg1, arg2, arg3, arg4) }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    fun <TArg1, TArg2, TArg3, TArg4, TArg5> Wrap(func: (TArg1, TArg2, TArg3, TArg4, TArg5) -> Any?):
        (TArg1, TArg2, TArg3, TArg4, TArg5) -> Unit
    {
        return {
            arg1, arg2, arg3, arg4, arg5 ->
            Submit(object: Message {
                override fun Invoke() { func(arg1, arg2, arg3, arg4, arg5) }

                override fun Reject(error: Throwable) {/* Rejection ignored. */}
            })
        }
    }

    /** Run the provided function in the context. Result is ignored as well as any exception.
     * Use Task when the result is needed.
     */
    fun <T> Run(func: () -> T)
    {
        Submit(object: Message {
            override fun Invoke() { func() }

            override fun Reject(error: Throwable) {/* Rejection ignored. */}
        })
    }
}
