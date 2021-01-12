/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred

class ConcatOperator<T> (private val inputs: Iterator<Observable<T>>): Observable.Source<T> {

    val output: Observable<T>

    override fun Get(): Deferred<Observable.Value<T>>
    {
        val nextDef: Deferred<Boolean>?
        val ret: Deferred<Observable.Value<T>> = synchronized(this) {
            nextDef = this.nextDef
            this.nextDef = null
            error?.also { return@synchronized Deferred.ForError(it) }
            val nextValue = nextValue
            if (nextValue != null) {
                this.nextValue = null
                return@synchronized Deferred.ForResult(nextValue)
            }
            val srcDef = Deferred.Create<Observable.Value<T>>()
            this.srcDef = srcDef
            srcDef
        }
        nextDef?.SetResult(true)
        return ret
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var srcDef: Deferred<Observable.Value<T>>? = null
    private var nextDef: Deferred<Boolean>? = null
    private var nextValue: Observable.Value<T>? = null
    private var error: Throwable? = null

    init {
        if (inputs.hasNext()) {
            inputs.next().Subscribe(this::OnNext)
        } else {
            nextValue = Observable.Value.None()
        }
        output = Observable.Create(this)
    }

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        val ret: Deferred<Boolean>?
        val action: (() -> Unit)? = synchronized(this) {
            val srcDef = srcDef
            if (error != null) {
                this.error = error
                ret = null
                if (srcDef != null) {
                    this.srcDef = null
                    return@synchronized { srcDef.SetError(error) }
                }
                return@synchronized null
            }
            if (value.isSet) {
                if (srcDef != null) {
                    ret = null
                    this.srcDef = null
                    return@synchronized { srcDef.SetResult(value) }
                }
                nextValue = value
                ret = Deferred.Create()
                nextDef = ret
                return@synchronized null
            }
            /* End of input stream. */
            ret = null
            if (inputs.hasNext()) {
                return@synchronized { inputs.next().Subscribe(this::OnNext) }
            }
            if (srcDef != null) {
                this.srcDef = null
                return@synchronized { srcDef.SetResult(value) }
            }
            nextValue = value
            null
        }
        action?.invoke()
        return ret
    }
}

fun <T> Observable.Companion.Concat(inputs: Iterator<Observable<T>>):
    Observable<T>
{
    return ConcatOperator(inputs).output
}

fun <T> Observable.Companion.Concat(vararg inputs: Observable<T>):
    Observable<T>
{
    return ConcatOperator(inputs.iterator()).output
}

fun <T> Observable<T>.Concat(inputs: Iterator<Observable<T>>):
    Observable<T>
{
    val list = ArrayList<Observable<T>>()
    list.add(this)
    list.addAll(inputs.asSequence())
    return ConcatOperator(list.iterator()).output
}

fun <T> Observable<T>.Concat(vararg inputs: Observable<T>):
    Observable<T>
{
    return Concat(inputs.iterator())
}
