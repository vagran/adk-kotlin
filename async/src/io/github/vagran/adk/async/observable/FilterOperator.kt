/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume

typealias FilterOperatorFunc<T> = suspend (T) -> Boolean

class FilterOperator<T>(input: Observable<T>,
                        private val func: FilterOperatorFunc<T>):
        ObservableOperator<T>() {

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        val result = NextInput()
        ProcessNext(value, error)
        return result
    }

    private fun ProcessNext(value: Observable.Value<T>, error: Throwable?)
    {
        if (error != null) {
            SetResult(null, error)
            return
        }
        if (!value.isSet) {
            SetResult(Observable.Value.None(), null)
            return
        }

        suspend {
            func(value.value)
        }.createCoroutine(object : Continuation<Boolean> {

            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<Boolean>)
            {
                result.fold({
                    isPassed ->
                    if (isPassed) {
                        SetResult(value, null)
                    } else {
                        ValueProcessed()
                    }
                }, {
                    exception ->
                    SetResult(null, exception)
                })
            }
        }).resume(Unit)
    }

    init {
        input.Subscribe(this::OnNext)
    }
}

fun <T> Observable<T>.Filter(func: FilterOperatorFunc<T>): Observable<T>
{
    return FilterOperator(this, func).output
}
