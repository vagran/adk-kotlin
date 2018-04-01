package com.ast.adk.async

/** Execution context which receives messages. */
interface Context {

    companion object {
        /** Bound context for the current thread. */
        //XXX make thread local property
        val Current: Context? = null
    }

    /** Submit a message. Either message.Invoke() or message.Reject() will be called inside this
     * context.
     */
    fun Submit(message: Message)
}