/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async

/** Create deferred which completes when all the specified deferred results are completed.
 * If error occurs the first one is set to the resulted deferred.
 */
fun Deferred.Companion.WhenAll(results: Collection<Deferred<*>>): Deferred<Unit>
{
    return Deferred.When(results.iterator(), true)
}

fun Deferred.Companion.WhenAll(vararg results: Deferred<*>): Deferred<Unit>
{
    return Deferred.When(results.iterator(), true)
}

/** Create deferred which completes when any of the specified deferred results is completed.
 * Error is propagated only from the first completed result.
 */
fun Deferred.Companion.WhenAny(results: Collection<Deferred<*>>): Deferred<Unit>
{
    return Deferred.When(results.iterator(), false)
}

fun Deferred.Companion.WhenAny(vararg results: Deferred<*>): Deferred<Unit>
{
    return Deferred.When(results.iterator(), false)
}

fun Deferred.Companion.When(results: Iterator<Deferred<*>>, all: Boolean): Deferred<Unit>
{
    class Aggregator {

        val result = Create<Unit>()
        private var iterDone = false
        private var numResults = 0
        private var numComplete = 0
        private var error: Throwable? = null
        private val subscriptions: ArrayList<Deferred.Subscription>? =
            if (all) null else ArrayList()

        init {
            while (results.hasNext()) {
                val def = results.next();
                synchronized(this) {
                    numResults++
                }
                val sn = def.Subscribe(this::OnComplete)
                subscriptions?.add(sn)
            }
            if (all) {
                val result = synchronized(this) {
                    iterDone = true
                    CheckComplete()
                }
                result?.invoke()
            }
        }

        @Suppress("UNUSED_PARAMETER")
        private fun OnComplete(value: Any?, error: Throwable?)
        {
            val result = synchronized(this) {
                if (!all && numComplete > 0) {
                    return
                }
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
            result?.invoke()
        }

        private fun CheckComplete(): (() -> Unit)?
        {
            if ((iterDone && all && numComplete == numResults) || (!all && numComplete > 0)) {
                return {
                    subscriptions?.forEach { it.Unsubscribe() }
                    if (error == null) {
                        result.SetResult(Unit)
                    } else {
                        result.SetError(error as Throwable)
                    }
                }
            }
            return null
        }
    }

    return Aggregator().result
}
