/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.async.Context
import io.github.vagran.adk.domain.httpserver.HttpError
import java.util.concurrent.locks.ReentrantLock

/** Prohibits entity modification when activity is running. */
class ActivityLock(val blockedError: () -> Nothing =
                       { throw HttpError(403, "The entity is currently blocked by running activity") }): Activity {

    /** Throws if activation disabled. */
    override fun Start()
    {
        lock.lock()
        if (isActive) {
            lock.unlock()
            throw IllegalStateException("Already active")
        }
        if (!isEnabled) {
            lock.unlock()
            throw IllegalStateException("Activation disabled")
        }
        isActive = true
        lock.unlock()
    }

    override fun Stop()
    {
        lock.lock()
        if (!isActive) {
            lock.unlock()
            throw IllegalStateException("Not active")
        }
        isActive = false
        lock.unlock()
    }

    fun Disable()
    {
        lock.lock()
        if (isActive) {
            lock.unlock()
            throw IllegalStateException("Cannot disable while active")
        }
        isEnabled = false
        lock.unlock()
    }

    /** Throws error if cannot acquire access. */
    fun <T> WithModifyAccess(block: () -> T): T
    {
        lock.lock()
        try {
            if (isActive) {
                blockedError()
            }
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** @param ctx Context which will own the lock. */
    suspend fun <T> WithModifyAccessAsync(ctx: Context, block: suspend () -> T): T
    {
        ctx.ResumeIn()
        lock.lock()
        try {
            if (isActive) {
                blockedError()
            }
            return block()
        } finally {
            ctx.ResumeIn()
            lock.unlock()
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val lock = ReentrantLock()
    private var isActive = false
    /** Is activation enabled */
    private var isEnabled = true
}
