package com.ast.adk.async

/** Temporal context which runs on current thread. */
class CurrentThreadContext: QueuedContext() {
    init {
        Start()
    }

    /** Runs the context. Blocks until the context is stopped by Stop() method. */
    fun Run()
    {
        Context.current = this
        while (!WaitAndProcess()) {}
    }
}
