package com.ast.adk.async

import com.ast.adk.utils.Log
import org.apache.logging.log4j.Logger
import java.util.*


class ThreadPoolContext(val name: String,
                        numThreads: Int): QueuedContext() {

    override fun Start()
    {
        for (t in threads) {
            t.Start()
        }
        super.Start()
        LockQueue {
            freeThreads.addAll(threads)
            Schedule()
        }
    }

    override fun Stop()
    {
        log.info("Context stop requested")
        super.Stop()
        /* Wait until all submitted messages are processed by workers. */
        LockQueue {
            while (!IsQueueEmpty()) {
                WaitQueue()
            }
        }
        for (t in threads) {
            t.Stop()
        }
    }

    override fun Submit(message: Message)
    {
        LockQueue {
            if (stopRequested) {
                throw Message.RejectedError("Context is already stopped")
            }
            queue.addLast(message)
            Schedule()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val log: Logger = Log.GetLogger("Ctx{$name}")

    private val threads: Array<ThreadContext> = Array(numThreads) {
        idx ->
        ThreadContext("$name-$idx")
    }
    /** Threads waiting for work. Use LIFO order to localize execution if possible. */
    private val freeThreads: ArrayDeque<ThreadContext> = ArrayDeque(numThreads)


    /** Schedule queued messages if possible. Should be called with queue locked. */
    private fun Schedule()
    {
        while (freeThreads.size > 0 && !IsQueueEmpty()) {
            ScheduleMessage(queue.removeFirst(), freeThreads.removeLast())
        }
    }

    private fun ScheduleMessage(message: Message, ctx: ThreadContext)
    {
        val wrapper = object: Message {
            override fun Invoke()
            {
                try {
                    message.Invoke()
                } finally {
                    OnWorkerFreed(ctx)
                }
            }

            override fun Reject(error: Throwable)
            {
                try {
                    message.Reject(error)
                } finally {
                    OnWorkerFreed(ctx)
                }
            }
        }
        ctx.Submit(wrapper)
    }

    private fun OnWorkerFreed(ctx: ThreadContext)
    {
        LockQueue {
            freeThreads.addLast(ctx)
            Schedule()
        }
    }
}
