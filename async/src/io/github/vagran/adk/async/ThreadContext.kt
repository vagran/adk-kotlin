/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

typealias ContextFailHandler = (msg: String, error: Throwable?) -> Unit

open class ThreadContext(val name: String,
                         private val failHandler: ContextFailHandler? = null):
    QueuedContext() {

    val thread: Thread = Thread(this::Run)

    override fun Start()
    {
        super.Start()
        LockQueue {
            isStarting = true
        }
        thread.name = name
        thread.start()
        WaitReady()
    }

    override fun Stop()
    {
        super.Stop()
        thread.join()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private var isStarting = false
    private var isRunning = false

    private fun Run()
    {
        try {
            OnStarted()
        } catch (e: Exception) {
            Error("Context start handler failed, start aborted", e)
            LockQueue {
                isStarting = false
                NotifyQueue()
            }
            return
        }

        LockQueue {
            isStarting = false
            isRunning = true
            NotifyQueue()
        }

        Context.current = this

        try {
            while (!WaitAndProcess()) {}
        } catch (e: Exception) {
            Error("Exception in context main loop", e)
            LockQueue {
                isRunning = false;
                NotifyQueue()
            }
            return
        }

        assert(IsQueueEmpty())

        try {
            OnStopped()
        } catch (e: Exception) {
            Error("Context stop handler failed", e)
        }

        LockQueue {
            isRunning = false
            NotifyQueue()
        }
    }

    private fun WaitReady()
    {
        LockQueue {
            while (isStarting) {
                WaitQueue()
            }
        }
    }

    protected fun Error(message: String, e: Throwable)
    {
        if (failHandler != null) {
            failHandler.invoke(message, e)
        } else {
            System.err.println("Error in $name:")
            e.printStackTrace(System.err)
        }
    }
}
