package com.ast.adk.async

/**
 * Message is submitted to an execution context.
 */
interface Message {

    /** Invoked in the target context. */
    fun Invoke()

    /** Invoked if the target context is not able to process this message. */
    fun Reject(error: Throwable)

}