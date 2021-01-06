/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.async.Context
import io.github.vagran.adk.async.Deferred
import io.github.vagran.adk.async.WhenAny
import io.github.vagran.adk.domain.httpserver.Endpoint
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.collections.ArrayList


/**
 * Each event may be published to a specific topic which is identified by a string. The event
 * payload is an arbitrary object which is then serialized to JSON. It is recommended to have simple
 * immutable DTO as event object.
 * The object is also DCO for long-poll-based events retrieval by front-end.
 * @param queueSize Number of last events queue to support zero-loss sequential retrieval.
 * @param httpPollInterval Maximal time to wait for next events in PollEvents() method. Used only if
 *      timerCtx provided.
 * @param timerCtx Context to use for scheduling poll timeout. No timeout if null.
 */
class EventBus(queueSize: Int = 1024,
               private val httpPollInterval: Duration = Duration.ofMinutes(1),
               private val timerCtx: Context? = null) {

    class EventRecord(val topic: String, val seq: Long, val event: Any?)

    class ClosedException: Exception("Bus closed")

    /**
     * Publish new event with the specified topic.
     */
    fun Publish(topic: String, event: Any?)
    {
        val def = synchronized(queue) {
            if (isClosed) {
                return
            }
            val key = Key(topic, curSeq++)
            queue[key] = event
            if (fifoFull) {
                queue.remove(fifoQueue[fifoIdx])
            }
            fifoQueue[fifoIdx] = key
            fifoIdx++
            if (fifoIdx >= fifoQueue.size) {
                fifoIdx = 0
                fifoFull = true
            }
            waitEntries.remove(topic)
        }
        def?.SetResult(Unit)
    }

    /**
     * @param topics List of topics to poll.
     * @param seq Event sequence number to start listening from. If called sequentially this should
     *      one more than maximal sequence number in previously returned records list. -1 to wait
     *      for new events.
     * @return List of available events. Method suspends until at least one event can be returned or
     *      httpPollInterval expired (empty list is returned in such case).
     * @throws ClosedException if the bus is closed.
     */
    @Endpoint
    suspend fun PollEvents(topics: List<String>, seq: Long = -1): List<EventRecord>
    {
        val result = ArrayList<EventRecord>()
        var _seq = seq
        val timeLimit = Instant.now().plus(httpPollInterval)

        while (true) {
            val def = synchronized(queue) {
                if (isClosed) {
                    throw ClosedException()
                }
                if (_seq != -1L) {
                    for (topic in topics) {
                        val key = Key(topic, _seq)
                        val subMap = queue.tailMap(key, true)
                        for (e in subMap.entries) {
                            val k = e.key
                            if (k.topic != topic) {
                                break
                            }
                            result.add(EventRecord(topic, k.seq, e.value))
                        }
                    }
                    if (result.isNotEmpty()) {
                        return result
                    }
                } else {
                    _seq = curSeq
                }
                val now = Instant.now()
                if (now.isAfter(timeLimit)) {
                    return emptyList()
                }
                val delay = Duration.between(now, timeLimit)
                val defs = ArrayList<Deferred<Unit>>()
                for (topic in topics) {
                    defs.add(waitEntries.computeIfAbsent(topic) { Deferred.Create() })
                }
                if (timerCtx != null) {
                    defs.add(Deferred.ForFunc { timerCtx.Delay(delay.toMillis()) })
                }
                Deferred.WhenAny(defs)
            }
            def.Await()
        }
    }

    /**
     * Terminates all active event waits with ClosedException.
     */
    fun Close()
    {
        synchronized(queue) {
            if (isClosed) {
                throw IllegalStateException("Already closed")
            }
            isClosed = true
            queue.clear()
        }
        waitEntries.values.forEach { it.SetError(ClosedException()) }
        waitEntries.clear()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private class Key(val topic: String, val seq: Long)

    private class KeyComparator: Comparator<Key> {

        override fun compare(k1: Key, k2: Key): Int
        {
            val tc = k1.topic.compareTo(k2.topic)
            if (tc != 0) {
                return tc
            }
            if (k1.seq < k2.seq) {
                return -1
            }
            if (k1.seq > k2.seq) {
                return 1
            }
            return 0
        }
    }

    private val queue = TreeMap<Key, Any?>(KeyComparator())
    private val fifoQueue = arrayOfNulls<Key>(queueSize)
    private var fifoIdx = 0
    private var fifoFull = false
    private var curSeq = 1L
    /** Indexed by topic. */
    private val waitEntries = TreeMap<String, Deferred<Unit>>()
    private var isClosed = false
}