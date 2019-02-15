package com.ast.adk.async

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Allows timed scheduling of submitted messages via SubmitScheduled() method. */
interface ScheduledContext {

    /** Token which can be used for scheduled message cancellation. */
    interface Token {
        /** Cancel scheduled message if possible.
         * @return True if cancelled, false if cannot cancel (already submitted for execution).
         */
        fun Cancel(): Boolean
    }

    /** Submit a message which will be invoked with the specified delay.
     * @param delay Delay in milliseconds.
     */
    fun SubmitScheduled(message: Message, delay: Long): Token

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
        ctx.ResumeIn {Delay(delay)}
    }
}
