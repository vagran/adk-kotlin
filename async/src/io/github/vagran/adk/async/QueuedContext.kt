/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

import java.util.*

/**
 * Context which has queue for submitted messages.
 */
abstract class QueuedContext: Context {

    /** Indicate the context is starting. Should be called before Run(). */
    open fun Start()
    {
        LockQueue {
            if (isStarted) {
                throw Exception("Already started")
            }
            isStarted = true
        }
    }

    /** Signal to stop processing loop. */
    open fun Stop()
    {
        LockQueue {
            if (!isStarted) {
                throw Exception("Not started")
            }
            if (stopRequested) {
                throw Exception("Stop already requested")
            }
            stopRequested = true
            NotifyQueue()
        }
    }

    override fun Submit(message: Message)
    {
        LockQueue {
            if (!isStarted) {
                throw Message.RejectedError("Context is not yet started")
            }
            if (stopRequested) {
                throw Message.RejectedError("Context is already stopped")
            }
            queue.addLast(message)
            NotifyQueue()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    protected val queue: Deque<Message> = ArrayDeque<Message>()

    /** Lock queue access. */
    protected inline fun <T> LockQueue(block: () -> T): T
    {
        return synchronized(queue, block)
    }

    /** Wait queue notification.
     * @param timeout Timeout in milliseconds, zero for unlimited wait.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected open fun WaitQueue(timeout: Long = 0)
    {
        (queue as java.lang.Object).wait(timeout)
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected open fun NotifyQueue()
    {
        (queue as java.lang.Object).notifyAll()
    }

    /** Should be called with queue lock acquired. */
    protected fun IsQueueEmpty() = queue.size == 0

    /** Called when context started. */
    protected open fun OnStarted()
    {}

    /** Called when context stopped. */
    protected open fun OnStopped()
    {}

    /** Process all available items in the submission queue.
     * @return True if stop requested, false otherwise.
     */
    protected fun ProcessSubmissionQueue(): Boolean
    {
        /* Limit processing round to current queue content. */
        var n = -1
        while (n > 0 || n == -1) {
            var msg: Message? = null
            val stopped = LockQueue {
                if (stopRequested && IsQueueEmpty()) {
                    NotifyQueue()
                    return@LockQueue true
                }
                if (n == -1) {
                    n = queue.size
                    if (n == 0) {
                        return@LockQueue false
                    }
                }
                msg = queue.pollFirst()
                false
            }
            if (stopped) {
                return true
            }
            if (msg == null) {
                break
            }
            n--
            try {
                msg!!.Invoke()
            } catch (e: Throwable) {
                System.err.println("Exception in message handler:")
                e.printStackTrace(System.err)
            }
        }
        return false
    }

    /** Wait and process incoming requests. Default implementation handles only submission queue.
     * Custom implementation could, for example, wait on I/O selectors. In such case custom
     * notification also should be implemented to wake up custom waiting.
     *
     * @param timeout Maximal time to wait in milliseconds, 0 for unlimited.
     *
     * @return True if stop requested, false otherwise.
     */
    protected open fun WaitAndProcess(timeout: Long = 0): Boolean
    {
        val result = LockQueue {
            if (stopRequested && IsQueueEmpty()) {
                NotifyQueue()
                return@LockQueue true
            }
            if (IsQueueEmpty()) {
                WaitQueue(timeout)
            }
            false
        }
        if (result) {
            return result
        }
        return ProcessSubmissionQueue()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    protected var stopRequested = false

    private var isStarted = false
}
