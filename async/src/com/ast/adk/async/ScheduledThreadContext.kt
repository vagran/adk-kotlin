package com.ast.adk.async

import java.util.*

/** Allows timed scheduling of submitted messages via SubmitScheduled() method. */
class ScheduledThreadContext(name: String): ThreadContext(name) {

    /** Token which can be used for scheduled message cancellation. */
    inner class Token {
        /** Cancel scheduled message if possible.
         * @return True if cancelled, false if cannot cancel (already submitted for execution).
         */
        fun Cancel(): Boolean
        {
            TODO()
        }
    }

    /** Submit a message which will be invoked with the specified delay.
     * @param delay Delay in milliseconds.
     */
    fun SubmitScheduled(message: Message, delay: Long): Token
    {
        TODO()
    }

    /** Suspend current coroutine for the specified delay.
     * @param delay Delay in milliseconds.
     */
    suspend fun Delay(delay: Long)
    {
        TODO()
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
        if (timeout < nextTime) {
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
        TODO()
    }
}
