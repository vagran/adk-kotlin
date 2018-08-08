package com.ast.adk.log

import java.util.concurrent.atomic.AtomicReferenceArray

/** Volatile queue to use in log messages queue. This class is not supposed to be used as
 * thread-safe out of the box. It just provides volatile access to queue data so that it can be used
 * by lock-less algorithms. It is up to the caller to ensure that the capacity is not exceeded
 * otherwise an exception is thrown.
 */
class VolatileQueue<T>(val capacity: Int) {

    fun Push(obj: T)
    {
        var idx = endIdx
        if (idx == startIdx) {
            throw Exception("Queue overflow")
        }
        if (idx == capacity) {
            idx = 0
        }
        queue[idx] = obj
        if (startIdx == -1) {
            startIdx = idx
        }
        endIdx = idx + 1
    }

    fun Pop(): T
    {
        var idx = startIdx
        if (idx == -1) {
            throw Exception("Queue underflow")
        }
        if (idx == capacity) {
            idx = 0
        }
        val result = queue[idx]
        startIdx = if (idx + 1 == endIdx) -1 else idx + 1
        return result
    }

    fun Poll(): T?
    {
        var idx = startIdx
        if (idx == -1) {
            return null
        }
        if (idx == capacity) {
            idx = 0
        }
        val result = queue[idx]
        startIdx = if (idx + 1 == endIdx) -1 else idx + 1
        return result
    }

    val size: Int
        get() {
            return when {
                startIdx == -1 -> 0
                endIdx > startIdx -> endIdx - startIdx
                else -> endIdx + capacity - startIdx
            }
        }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val queue = AtomicReferenceArray<T>(capacity)
    @Volatile private var startIdx = -1
    @Volatile private var endIdx = 0
}
