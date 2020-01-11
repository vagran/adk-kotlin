/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import java.io.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicLong


/**
 * Application-instance-locally-unique monotonically increased ID.
 */
//XXX make inline when inline classes stabilized
class LocalId(val value: Long): Comparable<LocalId>, Serializable {

    constructor():
        this(NextValue())

    constructor(s: String):
        this(java.lang.Long.parseLong(s, 16))

    val isZero: Boolean get() = value == 0L

    override fun equals(other: Any?): Boolean
    {
        val _other = other as? LocalId ?: return false
        return value == _other.value
    }

    override fun hashCode(): Int
    {
        return java.lang.Long.hashCode(value)
    }

    override fun compareTo(other: LocalId): Int
    {
        return value.compareTo(other.value)
    }

    override fun toString(): String
    {
        return value.toString(16)
    }

    companion object {
        private val counter = AtomicLong(System.nanoTime() and 0xffffffffL)
        private const val serialVersionUID = 1L

        private fun NextValue(): Long
        {
            val ts = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
            while (true) {
                val cnt = counter.get()
                val newCnt = if (ts > (cnt + 1) ushr 32) {
                    (ts shl 32) or ((cnt + 1) and 0xffffffffL)
                } else {
                    cnt + 1
                }
                if (counter.compareAndSet(cnt, newCnt)) {
                    return newCnt
                }
            }
        }

        val ZERO = LocalId(0L)
    }
}

