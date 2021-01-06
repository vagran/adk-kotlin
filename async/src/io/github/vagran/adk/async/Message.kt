/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

/**
 * Message is submitted to an execution context.
 */
interface Message {

    class RejectedError: Exception {

        constructor(message: String):
                super(message)

        constructor(message: String, cause: Throwable):
                super(message, cause)
    }

    /** Invoked in the target context. */
    fun Invoke()

    /** Invoked if the target context is not able to process this message. */
    fun Reject(error: Throwable)

}
