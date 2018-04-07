package com.ast.adk.async

import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger

open class ThreadContext(val name: String): QueuedContext() {

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
        log.info("Context stop requested")
        super.Stop()
        thread.join()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    protected val log: Logger = Log.GetLogger("Ctx{$name}")

    private var isStarting = false
    private var isRunning = false

    private fun Run()
    {
        log.info("Context started")
        try {
            OnStarted()
        } catch (e: Exception) {
            log.error("Context start handler failed, start aborted", e)
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

        try {
            while (!WaitAndProcess()) {}
        } catch (e: Exception) {
            log.fatal("Exception in context main loop", e)
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
            log.error("Context stop handler failed", e)
        }

        log.info("Context terminated")

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
}
