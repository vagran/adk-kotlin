/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.log

/** Logger name by convention is hierarchical, each component separated by dot. Logger name "root"
 * is reserved for root logger.
 */
class LoggerName: Comparable<LoggerName> {
    val components: List<String>

    companion object {
        val ROOT = LoggerName("")
    }

    constructor(nameStr: String)
    {
        var curIdx = 0
        val compList = ArrayList<String>()
        while (curIdx < nameStr.length) {
            val nextIdx = nameStr.indexOf('.', curIdx)
            if (nextIdx == -1) {
                if (curIdx < nameStr.length) {
                    compList.add(nameStr.substring(curIdx))
                }
                break
            }
            if (curIdx < nextIdx) {
                compList.add(nameStr.substring(curIdx, nextIdx))
            }
            curIdx = nextIdx + 1
        }
        components = compList
    }

    private constructor(components: List<String>)
    {
        this.components = ArrayList(components)
    }

    val length get() = components.size

    fun Prefix(length: Int): LoggerName
    {
        if (length < 0 || length > components.size) {
            throw IllegalArgumentException("Prefix length out of range")
        }
        if (length == 0) {
            return ROOT
        }
        if (length == components.size) {
            return this
        }
        return LoggerName(components.subList(0, length))
    }

    override fun toString(): String
    {
        return components.joinToString(".")
    }

    override fun equals(other: Any?): Boolean
    {
        return components.equals((other as LoggerName).components)
    }

    override fun hashCode(): Int
    {
        return components.hashCode()
    }

    override fun compareTo(other: LoggerName): Int
    {
        for (i in 0 until components.size) {
            if (i >= other.components.size) {
                return 1
            }
            val cmp = components[i].compareTo(other.components[i])
            if (cmp != 0) {
                return cmp
            }
        }
        if (other.components.size > components.size) {
            return -1
        }
        return 0
    }
}
