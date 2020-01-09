package com.ast.adk.async

import java.util.*

/** Asynchronous read-write lock. Multiple readers allowed, write is locked exclusively. */
class ReadWriteLock {

    fun AcquireRead(): Deferred<Unit>
    {
        synchronized(readQueue) {
            if (state >= 0 && writeQueue.isEmpty()) {
                state++
                return Deferred.Unit()
            }
            val def = Deferred.Create<Unit>()
            readQueue.addLast(def)
            return def
        }
    }

    fun AcquireWrite(): Deferred<Unit>
    {
        synchronized(readQueue) {
            if (state == 0) {
                state = -1
                return Deferred.Unit()
            }
            val def = Deferred.Create<Unit>()
            writeQueue.addLast(def)
            return def
        }
    }

    fun ReleaseRead()
    {
        val def = synchronized(readQueue) {
            if (state <= 0) {
                throw Error("Not locked for reading")
            }
            state--
            if (state == 0) {
                val def = writeQueue.pollFirst()
                if (def != null) {
                    state = -1
                }
                return@synchronized def
            }
            null
        }
        def?.SetResult(Unit)
    }

    fun ReleaseWrite()
    {
        val defs = synchronized(readQueue) {
            if (state != -1) {
                throw Error("Not locked for writing")
            }
            if (readQueue.isNotEmpty()) {
                val defs = readQueue.toList()
                readQueue.clear()
                state = defs.size
                return@synchronized defs
            }
            val def = writeQueue.pollFirst()
            if (def != null) {
                return@synchronized listOf(def)
            }
            state = 0
            null
        }
        defs?.forEach { it.SetResult(Unit) }
    }

    fun <T> SynchronizedRead(block: suspend () -> T): Deferred<T>
    {
        val result = Deferred.Create<T>()
        AcquireRead().Subscribe {
            _, error ->
            if (error != null) {
                result.SetError(error)
                return@Subscribe
            }
            Deferred.ForFunc(block).Subscribe {
                resultValue, _error ->
                ReleaseRead()
                if (_error != null) {
                    result.SetError(_error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    result.SetResult(resultValue as T)
                }
            }
        }
        return result
    }

    fun <T> SynchronizedWrite(block: suspend () -> T): Deferred<T>
    {
        val result = Deferred.Create<T>()
        AcquireWrite().Subscribe {
            _, error ->
            if (error != null) {
                result.SetError(error)
                return@Subscribe
            }
            Deferred.ForFunc(block).Subscribe {
                resultValue, _error ->
                ReleaseWrite()
                if (_error != null) {
                    result.SetError(_error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    result.SetResult(resultValue as T)
                }
            }
        }
        return result
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val readQueue = ArrayDeque<Deferred<Unit>>()
    private val writeQueue = ArrayDeque<Deferred<Unit>>()
    /** 0 - unlocked, -1 - writing, positive value - number of readers. */
    private var state = 0
}
