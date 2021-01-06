/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

import java.util.*


class ThreadPoolContext(val name: String,
                        numThreads: Int,
                        private val failHandler: ContextFailHandler? = null):
    QueuedContext() {

    override fun Start()
    {
        for (t in threads) {
            t.Start()
        }
        super.Start()
        LockQueue {
            freeThreads.addAll(threads)
        }
        Schedule()
    }

    override fun Stop()
    {
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
        }
        Schedule()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private val threads: Array<ThreadContext> = Array(numThreads) {
        idx ->
        ThreadContext("$name-$idx", failHandler)
    }
    /** Threads waiting for work. Use LIFO order to localize execution if possible. */
    private val freeThreads: ArrayDeque<ThreadContext> = ArrayDeque(numThreads)


    /** Schedule queued messages if possible. */
    private fun Schedule()
    {
        var message: Message? = null
        var ctx: ThreadContext? = null
        while (true) {
            if (!LockQueue {
                if (freeThreads.size > 0 && !IsQueueEmpty()) {
                    message = queue.removeFirst()
                    ctx = freeThreads.removeLast()
                    if (stopRequested && IsQueueEmpty()) {
                        NotifyQueue()
                    }
                    return@LockQueue true
                }
                false
            }) {
                return
            }
            ScheduleMessage(message as Message, ctx as ThreadContext)
        }
    }

    private fun ScheduleMessage(message: Message, ctx: ThreadContext)
    {
        val wrapper = object: Message {
            override fun Invoke()
            {
                try {
                    message.Invoke()
                } catch (e: Throwable) {
                    Error("Exception in message handler", e)
                } finally {
                    OnWorkerFreed(ctx)
                }
            }

            override fun Reject(error: Throwable)
            {
                try {
                    message.Reject(error)
                } catch (e: Throwable) {
                    Error("Exception in message reject handler", e)
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
        }
        Schedule()
    }

    private fun Error(message: String, e: Throwable)
    {
        if (failHandler != null) {
            failHandler.invoke(message, e)
        } else {
            System.err.println("Error in $name:")
            e.printStackTrace(System.err)
        }
    }
}
