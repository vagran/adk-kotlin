/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log

import java.util.concurrent.atomic.AtomicInteger

/**
 * Queue for transferring log messages asynchronously to appenders. Should work mostly lock-less.
 * Supports multiple concurrent producers and a single consumer.
 * @param maxSize Maximal queue size. When the limit is reached the push method either block or new
 *      message is discarded depending on isBlocking parameter.
 * @param emptyCheckInterval Interval in ms for checking if queue is not empty on consumer side.
 *      Consumer is periodically waken up to check if queue is not empty to minimize synchronization
 *      on the producers side. If the queue is filled above a threshold level, a producer will wake
 *      up the consumer if it missed this.
 */
class LogQueue<T>(private val maxSize: Int,
                  private val isBlocking: Boolean,
                  private val emptyCheckInterval: Long) {

    /** Push message into the queue. May block if the queue is full and isBlocking parameter is set.
     * The message is instantly discarded if the queue stopped.
     * @return True if the message is queued, false if discarded for any reason.
     */
    fun Push(msg: T): Boolean
    {
        while (true) {
            val curState = state.get()

            if (curState == STATE_STOPPED) {
                /* Discard the message. */
                return false
            }

            if (curState == STATE_WAIT_FULL) {
                WaitFull()
                continue
            }

            if (curState != STATE_READY &&
                curState != STATE_WAIT_EMPTY &&
                curState != STATE_WAIT_EMPTY_FILLED) {

                continue
            }

            if (!state.compareAndSet(curState, STATE_PUSH)) {
                continue
            }

            val size = queue.size
            if (size >= maxSize) {
                if (isBlocking) {
                    state.set(STATE_WAIT_FULL)
                    WaitFull()
                    continue
                }
                /* Discard the message. */
                state.set(STATE_READY)
                return false
            }

            val doNotify = curState == STATE_WAIT_EMPTY_FILLED && size >= wakeThreshold
            queue.Push(msg)

            state.set(
                if (curState == STATE_WAIT_EMPTY || curState == STATE_WAIT_EMPTY_FILLED) {
                    STATE_WAIT_EMPTY_FILLED
                } else {
                    STATE_READY
                })

            if (doNotify) {
                Notify()
            }
            return true
        }
    }

    /** Pop the message from the queue. May block if queue is empty and running. Returns null if
     * no more message after the queue is stopped.
     * @param idleFunc Invoked when there are no more messages in the queue and it is about to wait.
     */
    fun Pop(idleFunc: (() -> Unit)? = null): T?
    {
        while (true) {
            val curState = state.get()

            if (curState == STATE_STOPPED) {
                return queue.Poll()
            }

            if (curState == STATE_WAIT_EMPTY) {
                throw Error("Multiple consumers not supported")
            }

            if (curState != STATE_READY &&
                curState != STATE_WAIT_FULL &&
                curState != STATE_WAIT_EMPTY_FILLED) {

                continue
            }

            if (!state.compareAndSet(curState, STATE_POP)) {
                continue
            }

            val msg = queue.Poll()

            if (msg == null) {
                state.set(STATE_WAIT_EMPTY)
                WaitEmpty(idleFunc)
                continue
            }

            state.set(STATE_READY)

            if (curState == STATE_WAIT_FULL) {
                Notify()
            }

            return msg
        }
    }

    /** Stop the queue so that new messages are discarded. Any blocked calls are unblocked. */
    fun Stop()
    {
        while (true) {
            val curState = state.get()

            if (curState == STATE_STOPPED) {
                return
            }

            if (curState != STATE_READY &&
                curState != STATE_WAIT_EMPTY &&
                curState != STATE_WAIT_FULL &&
                curState != STATE_WAIT_EMPTY_FILLED) {

                continue
            }

            if (!state.compareAndSet(curState, STATE_STOPPED)) {
                continue
            }

            Notify()
            return
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val queue = VolatileQueue<T>(maxSize)
    private val state = AtomicInteger(STATE_READY)
    private val wakeThreshold = maxSize / 2

    init {
        if (maxSize < 2) {
            throw IllegalArgumentException("maxSize should be at least 2: $maxSize")
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun WaitFull()
    {
        synchronized(queue) {
            while (state.get() == STATE_WAIT_FULL) {
                (queue as Object).wait()
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun WaitEmpty(idleFunc: (() -> Unit)?)
    {
        while (state.get() == STATE_WAIT_EMPTY) {
            idleFunc?.invoke()
            synchronized(queue) {
                if (state.get() == STATE_WAIT_EMPTY) {
                    (queue as Object).wait(emptyCheckInterval)
                }
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Notify()
    {
        synchronized(queue) {
            (queue as Object).notifyAll()
        }
    }
}

private const val STATE_READY = 0
private const val STATE_PUSH =1
private const val STATE_POP = 2
private const val STATE_WAIT_FULL = 3
private const val STATE_WAIT_EMPTY = 4
private const val STATE_WAIT_EMPTY_FILLED = 5
private const val STATE_STOPPED = 6
