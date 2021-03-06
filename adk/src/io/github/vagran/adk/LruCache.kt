/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * @param timeout Entry expiration timeout in ms.
 */
class LruCache<T>(val timeout: Long,
                  private val closeHandler: ((data: T) -> Unit)? = null) {

    /** Remove entry from the cache.
     * @return true if entry removed, false if the specified entry not found.
     */
    fun Remove(id: String): Boolean
    {
        return entries.remove(id) != null
    }

    /** Add entry to the cache.
     * @return ID for the new entry.
     */
    fun Add(data: T): String
    {
        val e = Entry(data, NewId())
        entries[e.id] = e
        e.Refresh()
        return e.id
    }

    fun Add(fabric: (id: String) -> T): T
    {
        val id = NewId()
        val e = Entry(fabric(id), id)
        entries[id] = e
        e.Refresh()
        return e.data
    }

    /** Get entry with specified ID.
     * @return null if entry not found.
     */
    fun Get(id: String): T?
    {
        while (true) {
            val e = entries[id] ?: return null
            if (!e.Refresh()) {
                continue
            }
            return e.data
        }
    }

    /** Try to get entry with the specified ID, create and insert new one if not found.
     * @fabric Fabric function for new entry creation.
     * @return Either found or created entry.
     */
    fun ComputeIfAbsent(id: String, fabric: () -> T): T
    {
        while (true) {
            val e = entries.computeIfAbsent(id) { Entry(fabric(), id) }
            if (!e.Refresh()) {
                continue
            }
            return e.data
        }
    }

    /** Cleanup expired entries. */
    fun Cleanup()
    {
        val now = System.currentTimeMillis()
        while (true) {
            val e = sessionLruList.firstEntry() ?: return
            val time = e.key
            if (time > now) {
                return
            }
            val entry = e.value
            if (!entry.expires.compareAndSet(time, -1)) {
                continue
            }
            sessionLruList.remove(time)
            entries.remove(entry.id)
            closeHandler?.invoke(entry.data)
        }
    }

    /** Remove all entries. */
    fun Clear()
    {
        if (closeHandler != null) {
            entries.values.forEach { closeHandler.invoke(it.data) }
        }
        entries.clear()
        sessionLruList.clear()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val random = Random()
    private val entries = ConcurrentHashMap<String, Entry>()
    private val sessionLruList = ConcurrentSkipListMap<Long, Entry>()

    private inner class Entry(val data: T, val id: String) {
        /** Expiration time by System.currentTimeMillis() clock. -1 if already expired and clean up
         * pending. 0 if not yet in list.
         */
        val expires = AtomicLong(0)

        /** Renew expiration timer.
         * @return True if renewed, false if the session is already expired and should not be used.
         */
        fun Refresh(): Boolean
        {
            val newValue = System.currentTimeMillis() + timeout
            while (true) {
                val curValue = expires.get()
                if (curValue < 0) {
                    return false
                }
                if (expires.compareAndSet(curValue, newValue)) {
                    if (curValue != 0L) {
                        sessionLruList.remove(curValue)
                    }
                    sessionLruList[newValue] = this
                    return true
                }
            }
        }
    }

    private fun NewId(): String
    {
        return abs(random.GetLong()).toString(Character.MAX_RADIX)
    }
}
