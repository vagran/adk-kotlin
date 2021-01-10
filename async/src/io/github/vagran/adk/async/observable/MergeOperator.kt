/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred
import java.util.*
import kotlin.collections.ArrayList

//XXX test recursion
internal class MergeOperator<T>(inputs: Iterator<Observable<T>>,
                                private val delayError: Boolean): Observable.Source<T> {

    val output: Observable<T>

    override fun Get(): Deferred<Observable.Value<T>>
    {
        var queueEmptyDef: Deferred<Boolean>? = null
        var complete: (() -> Unit)? = null
        val ret = synchronized(queue) {
            val v = queue.pollFirst()
            if (v != null) {
                if (queue.isEmpty()) {
                    queueEmptyDef = this.queueEmptyDef
                    this.queueEmptyDef = Deferred.Create()
                }
                Deferred.ForResult(v)
            } else {
                val def = Deferred.Create<Observable.Value<T>>()
                nextDef = def
                complete = CheckComplete()
                def
            }
        }
        queueEmptyDef?.SetResult(true)
        complete?.invoke()
        return ret
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private val queue = ArrayDeque<Observable.Value<T>>()
    private var numComplete = 0
    private var error: Throwable? = null
    /** Negative until all inputs iterated. */
    private var numInputs = 0
    private var nextDef: Deferred<Observable.Value<T>>? = null
    private var queueEmptyDef = Deferred.Create<Boolean>()
    private val subscriptions = ArrayList<Observable.Subscription>()

    init {
        while (inputs.hasNext()) {
            val next = inputs.next()
            synchronized(queue) {
                numInputs--
                subscriptions.add(next.Subscribe(this::OnNext))
            }
        }
        synchronized(queue) {
            numInputs = -numInputs
        }
        output = Observable.Create(this)
    }

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        if (error != null) {
            synchronized(queue) {
                if (this.error != null) {
                    this.error!!.addSuppressed(error)
                } else {
                    this.error = error
                }
                numComplete++
                CheckComplete()
            }?.invoke()
            return null
        }
        if (!value.isSet) {
            synchronized(queue) {
                numComplete++
                CheckComplete()
            }?.invoke()
            return null
        }
        val nextDef: Deferred<Observable.Value<T>>?
        val queueEmptyDef: Deferred<Boolean>?
        synchronized(queue) {
            nextDef = this.nextDef
            queueEmptyDef = this.queueEmptyDef
            if (nextDef == null) {
                queue.addLast(value)
            } else {
                this.nextDef = null
            }
        }
        return if (nextDef != null) {
            nextDef.SetResult(value)
            null
        } else {
            queueEmptyDef
        }
    }

    /** Should be called under lock. */
    private fun CheckComplete(): (() -> Unit)?
    {
        val nextDef = nextDef ?: return null
        val error = this.error
        if ((error != null && !delayError) || numInputs == numComplete) {
            this.nextDef = null
            if (error != null) {
                return { nextDef.SetError(error) }
            } else {
                return { nextDef.SetResult(Observable.Value.None()) }
            }
        }
        return null
    }
}

/** Merge values from several observables.
 * @param delayError All inputs are drained fully before emitting (if any seen) when true. Error is
 * emitted as soon as possible if false. First error is emitted. Rest errors are added to suppressed
 * list when true.
 */
fun <T> Observable.Companion.Merge(inputs: Iterator<Observable<T>>, delayError: Boolean = true):
    Observable<T>
{
    return MergeOperator(inputs, delayError).output
}

fun <T> Observable.Companion.Merge(vararg inputs: Observable<T>):
    Observable<T>
{
    return MergeOperator(inputs.iterator(), false).output
}

fun <T> Observable.Companion.Merge(delayError: Boolean, vararg inputs: Observable<T>):
    Observable<T>
{
    return MergeOperator(inputs.iterator(), delayError).output
}
