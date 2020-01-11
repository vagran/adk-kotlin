/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred

/** Result is provided when the first item is received. Error is set if the observable is completed
 * without items.
 */
fun <T> Observable<T>.First(): Deferred<T>
{
    val result = Deferred.Create<T>()
    Subscribe { value, error ->
        if (error != null) {
            result.SetError(error)
        } else if (!value.isSet) {
            result.SetError(Error("At least one value expected"))
        } else {
            result.SetResult(value.value)
        }
        null
    }
    return result
}

/** Result is provided when one item is received and the observable completes. Error set if the
 * observable is completed without items or has more than one item.
 */
fun <T> Observable<T>.One(): Deferred<T>
{
    val result = Deferred.Create<T>()

    var storedResult: Observable.Value<T>? = null

    SubscribeVoid { value, error ->
        when {
            error != null ->
                result.SetError(error)

            !value.isSet ->
                if (storedResult == null) {
                    result.SetError(Error("At least one value expected"))
                } else {
                    result.SetResult(storedResult!!.value)
                }

            else -> {
                if (storedResult != null) {
                    result.SetError(Error("Only one value expected"))
                }
                storedResult = value
            }
        }
    }
    return result
}

fun <T> Observable<T>.OnOneOrNone(): Deferred<Observable.Value<T>>
{
    val result = Deferred.Create<Observable.Value<T>>()

    var storedResult: Observable.Value<T>? = null

    SubscribeVoid { value, error ->
        when {
            error != null ->
                result.SetError(error)

            !value.isSet ->
                if (storedResult == null) {
                    result.SetResult(Observable.Value.None())
                } else {
                    result.SetResult(storedResult!!)
                }

            else -> {
                if (storedResult != null) {
                    result.SetError(Error("Only one value expected"))
                }
                storedResult = value
            }
        }
    }
    return result
}
