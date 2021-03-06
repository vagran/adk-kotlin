/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred
import kotlin.coroutines.*

typealias MapOperatorFunc<T, U> = suspend (T) -> U

class MapOperator<T, U>(input: Observable<T>,
                        private val func: MapOperatorFunc<T, U>):
        ObservableOperator<U>() {

    fun Close()
    {
        subscription.Unsubscribe()
    }

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
        }.createCoroutine(object: Continuation<U> {

            override val context: CoroutineContext = EmptyCoroutineContext

            override fun resumeWith(result: Result<U>)
            {
                result.fold({ SetResult(Observable.Value.Of(it), null) },
                            { SetResult(null, it) })
            }
        }).resume(Unit)
    }

    private val subscription: Observable.Subscription = input.Subscribe(this::OnNext)
}

fun <T, U> Observable<T>.Map(func: MapOperatorFunc<T, U>): Observable<U>
{
    return MapOperator(this, func).output
}
