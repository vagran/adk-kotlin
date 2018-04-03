package com.ast.adk.async

/**
 * Once submitted to the target context the task execution begins.
 * @param handler Handler which is invoked in the target context and provides task result.
 */
class Task<T>(private val handler: () -> T): Message {

    /** Task result provided by the task handler when complete. */
    val result: Deferred<T> = Deferred.Create()

    /**
     * @param handler Handler which is invoked in the target context and provides task result. The
     *      handler should ensure that all its continuations are run in the target context if
     *      needed (external coroutines should be wrapped by Context.XXX()).
     */
    constructor(handler: suspend () -> T)
    {

    }

    override fun Invoke()
    {

    }

    override fun Reject(error: Throwable)
    {

    }

}
