package com.ast.adk.log

import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Queue for transferring log messages asynchronously to appenders. Should work mostly lock-less.
 * @param maxSize Maximal queue size. When the limit is reached the push method either block or new
 *      message is discarded depending on isBlocking parameter.
 */
class LogQueue<T>(private val maxSize: Int,
                  private val isBlocking: Boolean) {

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

            if (curState != STATE_READY && curState != STATE_WAIT_EMPTY) {
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

            val doNotify = curState == STATE_WAIT_EMPTY && size >= maxSize / 2
            queue.addLast(msg)
            state.set(STATE_READY)
            if (doNotify) {
                Notify()
            }
            return true
        }
    }

    /** Pop the message from the queue. May block if queue is empty and running. Returns null if
     * no more message after the queue is stopped.
     */
    fun Pop(): T?
    {
        while (true) {
            val curState = state.get()

            if (curState == STATE_STOPPED) {
                return queue.pollFirst()
            }

            if (curState == STATE_WAIT_EMPTY) {
                /* Multiple consumers supported. */
                WaitEmpty()
                continue
            }

            if (curState != STATE_READY && curState != STATE_WAIT_FULL) {
                continue
            }

            if (!state.compareAndSet(curState, STATE_POP)) {
                continue
            }

            val msg = queue.pollFirst()

            if (msg == null) {
                state.set(STATE_WAIT_EMPTY)
                WaitEmpty()
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
                curState != STATE_WAIT_FULL) {

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
    private val queue = ArrayDeque<T>(maxSize)
    private val state = AtomicInteger(STATE_READY)

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
                (queue as java.lang.Object).wait()
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun WaitEmpty()
    {
        synchronized(queue) {
            while (state.get() == STATE_WAIT_EMPTY) {
                (queue as java.lang.Object).wait(EMPTY_CHECK_INTERVAL)
            }
        }
    }

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    private fun Notify()
    {
        synchronized(queue) {
            (queue as java.lang.Object).notifyAll()
        }
    }
}

private const val STATE_READY = 0
private const val STATE_PUSH =1
private const val STATE_POP = 2
private const val STATE_WAIT_FULL = 3
private const val STATE_WAIT_EMPTY = 4
private const val STATE_STOPPED = 5

/** Interval in ms for checking if queue is not empty on consumer side. */
private const val EMPTY_CHECK_INTERVAL = 100L
