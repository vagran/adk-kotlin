package com.ast.adk.async

import java.util.*

/**
 * Context which has queue for submitted messages.
 */
abstract class QueuedContext: Context {

    /** Indicate the context is starting. Should be called before Run(). */
    fun Start()
    {
        LockQueue {
            isStarting = true
        }
    }

    /** Signal to stop processing loop. */
    fun Stop()
    {
        LockQueue {
            if (stopRequested) {
                throw RuntimeException("Stop already requested")
            }
            stopRequested = true
            NotifyQueue()
        }
    }

    override fun Submit(message: Message)
    {
        val error = LockQueue {
            if (stopRequested) {
                return@LockQueue RejectedError("Context is already stopped")
            }
            queue.addLast(message)
            NotifyQueue()
            null
        }
        if (error != null) {
            message.Reject(error)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    /** Lock queue access. */
    protected fun <T> LockQueue(block: () -> T): T
    {
        return synchronized(queue, block)
    }

    /** Wait queue notification.
     * @param timeout Timeout in milliseconds, negative for unlimited wait.
     */
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected fun WaitQueue(timeout: Long = -1)
    {
        if (timeout < 0) {
            (queue as java.lang.Object).wait(timeout)
        } else {
            (queue as java.lang.Object).wait()
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    protected fun NotifyQueue()
    {
        (queue as java.lang.Object).notifyAll()
    }

    /** Should be called with queue lock acquired. */
    protected fun IsQueueEmpty() = queue.size != 0

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
            msg!!.Invoke()
        }
        return false
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var isRunning = false
    private var isStarting = false
    private var stopRequested = false

    private val queue: Deque<Message> = ArrayDeque<Message>()


}
