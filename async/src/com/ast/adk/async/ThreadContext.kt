package com.ast.adk.async

import com.ast.adk.Log
import org.apache.logging.log4j.Logger

open class ThreadContext(val name: String,
                         enableLogging: Boolean = true):
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
        log?.info("Context stop requested")
        super.Stop()
        thread.join()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    protected val log: Logger? = if (enableLogging) Log.GetLogger("Ctx{$name}") else null

    private var isStarting = false
    private var isRunning = false

    private fun Run()
    {
        log?.info("Context started")
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

        log?.info("Context terminated")

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

    private fun Error(message: String, e: Throwable)
    {
        if (log != null) {
            log.error(message, e)
        } else {
            System.err.println("$name: ${Log.GetStackTrace(e)}")
        }
    }
}
