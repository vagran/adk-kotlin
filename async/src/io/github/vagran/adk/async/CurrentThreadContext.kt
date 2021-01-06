/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

/** Temporal context which runs on current thread. */
class CurrentThreadContext: QueuedContext() {
    init {
        Start()
    }

    /** Runs the context. Blocks until the context is stopped by Stop() method. */
    fun Run()
    {
        Context.current = this
        @Suppress("ControlFlowWithEmptyBody")
        while (!WaitAndProcess()) {}
    }
}
