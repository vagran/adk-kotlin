/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

/** Create deferred which completes when all the specified deferred results are completed.
 * If error occurs the first one is set to the resulted deferred.
 */
fun Deferred.Companion.When(results: Collection<Deferred<*>>): Deferred<Unit>
{
    return Deferred.When(results.iterator())
}

fun Deferred.Companion.When(vararg results: Deferred<*>): Deferred<Unit>
{
    return Deferred.When(results.iterator())
}

fun Deferred.Companion.When(results: Iterator<Deferred<*>>): Deferred<Unit>
{
    class Aggregator {

        val result = Create<Unit>()
        private var iterDone = false
        private var numResults = 0
        private var numComplete = 0
        private var error: Throwable? = null

        init {
            while (results.hasNext()) {
                val def = results.next();
                synchronized(this) {
                    numResults++
                }
                def.Subscribe(this::OnComplete)
            }
            synchronized(this) {
                iterDone = true
                CheckComplete()
            }
        }

        @Suppress("UNUSED_PARAMETER")
        private fun OnComplete(value: Any?, error: Throwable?)
        {
            synchronized(this) {
                numComplete++
                if (error != null) {
                    if (this.error == null) {
                        this.error = error
                    } else {
                        this.error!!.addSuppressed(error)
                    }
                }
                CheckComplete()
            }
        }

        private fun CheckComplete()
        {
            if (iterDone && numComplete == numResults) {
                if (error == null) {
                    result.SetResult(Unit)
                } else {
                    result.SetError(error as Throwable)
                }
            }
        }
    }

    return Aggregator().result
}
