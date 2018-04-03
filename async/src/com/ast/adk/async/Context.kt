package com.ast.adk.async

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
}
