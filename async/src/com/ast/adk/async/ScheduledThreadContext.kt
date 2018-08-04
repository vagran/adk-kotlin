package com.ast.adk.async

import java.util.*
import kotlin.coroutines.experimental.suspendCoroutine

/** Allows timed scheduling of submitted messages via SubmitScheduled() method. */
class ScheduledThreadContext(name: String,
                             enableLogging: Boolean = true):
    ThreadContext(name, enableLogging = enableLogging) {

    /** Token which can be used for scheduled message cancellation.
     * @param fireTime Firing time as returned by System.nanoTime().
     */
    inner class Token internal constructor(internal val message: Message,
                                           internal val fireTime: Long) {

        /** Cancel scheduled message if possible.
         * @return True if cancelled, false if cannot cancel (already submitted for execution).
         */
        fun Cancel(): Boolean
        {
            return LockQueue {
                if (!isScheduled) {
                    return@LockQueue false
                }
                if (isCancelled) {
                    throw Exception("Already cancelled")
                }
                isCancelled = true
                isScheduled = false
                var t = scheduledMessages[fireTime]
                if (t == this) {
                    if (next == null) {
                        val firstTime = scheduledMessages.firstKey()
                        scheduledMessages.remove(fireTime)
                        if (fireTime == firstTime) {
                            NotifyQueue()
                        }
                    } else {
                        scheduledMessages[fireTime] = next!!
                    }
                    return@LockQueue true
                }
                while (t!!.next != null) {
                    if (t.next == this) {
                        t.next = next
                        return@LockQueue true
                    }
                    t = t.next
                }
                true
            }
        }

        internal var next: Token? = null
        internal var isScheduled = true
        private var isCancelled = false
    }

    /** Submit a message which will be invoked with the specified delay.
     * @param delay Delay in milliseconds.
     */
    fun SubmitScheduled(message: Message, delay: Long): Token
    {
        val token = Token(message, System.nanoTime() + delay * 1_000_000)
        LockQueue {
            if (stopRequested) {
                throw Message.RejectedError("Context is already stopped")
            }
            val existing = scheduledMessages.put(token.fireTime, token)
            if (existing != null) {
                token.next = existing
            }
            NotifyQueue()
        }
        return token
    }

    /** Suspend current coroutine for the specified delay.
     * @param delay Delay in milliseconds.
     */
    suspend fun Delay(delay: Long)
    {
        return suspendCoroutine {
            cont ->

            SubmitScheduled(object: Message {
                override fun Invoke()
                {
                    cont.resume(Unit)
                }

                override fun Reject(error: Throwable)
                {
                    cont.resumeWithException(error)
                }
            }, delay)
        }
    }

    /** Suspend current coroutine for the specified delay ensuring the continuation runs in the
     * specified context.
     * @param delay Delay in milliseconds.
     * @param ctx Continuation context.
     */
    suspend fun Delay(delay: Long, ctx: Context)
    {
        ctx.ResumeIn({Delay(delay)})
    }

    override fun Stop()
    {
        super.Stop()
        while (true) {
            var tokens: Token? =
                LockQueue {
                    val tokens = scheduledMessages.firstEntry() ?: return@LockQueue null
                    scheduledMessages.remove(tokens.key)
                    var t: Token? = tokens.value
                    while (t != null) {
                        t.isScheduled = false
                        t = t.next
                    }
                    return@LockQueue tokens.value
                } ?: break

            while (tokens != null) {
                try {
                    tokens.message.Reject(Message.RejectedError("Context terminated"))
                } catch (e: Throwable) {
                    log?.error("Exception in message reject handler", e)
                }
                tokens = tokens.next
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Scheduled messages indexed by firing time. */
    private val scheduledMessages: TreeMap<Long, Token> = TreeMap()

    override fun WaitAndProcess(timeout: Long): Boolean
    {
        var nextTime = ProcessScheduledMessages()
        if (nextTime != 0L) {
            nextTime /= 1_000_000
            if (nextTime == 0L) {
                nextTime = 1
            }
        }
        if (timeout != 0L && timeout < nextTime) {
            nextTime = timeout
        }
        return super.WaitAndProcess(nextTime)
    }

    /** Process scheduled messages, submit expired ones.
     *
     * @return Time until next message, ns. Zero for unlimited wait.
     */
    private fun ProcessScheduledMessages(): Long
    {
        val curTime = System.nanoTime()
        while (true) {
            var tokens: Token? = null
            val time = LockQueue {
                val e = scheduledMessages.firstEntry()
                if (e == null) {
                    return@LockQueue 0L
                }
                val fireTime = e.key
                if (fireTime > curTime) {
                    return@LockQueue fireTime - curTime
                }
                tokens = e.value
                scheduledMessages.remove(e.key)
                var t = tokens
                do {
                    t!!.isScheduled = false
                    t = t.next
                } while (t != null)
                0L
            }
            if (tokens == null) {
                return time
            }
            while (tokens != null) {
                try {
                    Submit(tokens!!.message)
                } catch (e: Throwable) {
                    try {
                        tokens!!.message.Reject(e)
                    } catch (_e: Throwable) {
                        log?.error("Exception in message reject handler", _e)
                    }
                }
                tokens = tokens!!.next
            }
        }
    }
}
