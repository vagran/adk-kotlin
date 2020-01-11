/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

import java.util.*

/** Allows timed scheduling of submitted messages via SubmitScheduled() method. */
open class ScheduledThreadContext(name: String,
                                  failHandler: ContextFailHandler? = null):
    ThreadContext(name, failHandler) {

    /** Token which can be used for scheduled message cancellation.
     * @param fireTime Firing time as returned by System.nanoTime().
     */
    inner class TokenImpl
        internal constructor(internal val message: Message, internal val fireTime: Long):
        Context.SchedulingToken {

        /** Cancel scheduled message if possible.
         * @return True if cancelled, false if cannot cancel (already submitted for execution).
         */
        override fun Cancel(): Boolean
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

        internal var next: TokenImpl? = null
        internal var isScheduled = true
        private var isCancelled = false
    }

    /** Submit a message which will be invoked with the specified delay.
     * @param delay Delay in milliseconds.
     */
    override fun SubmitScheduled(message: Message, delay: Long): Context.SchedulingToken
    {
        val token = TokenImpl(message, System.nanoTime() + delay * 1_000_000)
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

    override fun Stop()
    {
        super.Stop()
        while (true) {
            var tokens: TokenImpl? =
                LockQueue {
                    val tokens = scheduledMessages.firstEntry() ?: return@LockQueue null
                    scheduledMessages.remove(tokens.key)
                    var t: TokenImpl? = tokens.value
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
                    Error("Exception in message reject handler", e)
                }
                tokens = tokens.next
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Scheduled messages indexed by firing time. */
    private val scheduledMessages: TreeMap<Long, TokenImpl> = TreeMap()

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
            var tokens: TokenImpl? = null
            val time = LockQueue {
                val e = scheduledMessages.firstEntry() ?: return@LockQueue 0L
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
                        _e.addSuppressed(e)
                        Error("Exception in message reject handler", _e)
                    }
                }
                tokens = tokens!!.next
            }
        }
    }
}
