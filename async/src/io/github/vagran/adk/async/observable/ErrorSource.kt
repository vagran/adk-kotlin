/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred

class ErrorSource<T>(private val error: Throwable): Observable.Source<T> {
    override fun Get(): Deferred<Observable.Value<T>>
    {
        return Deferred.ForError(error)
    }
}

fun <T> Observable.Companion.ForError(error: Throwable): Observable<T>
{
    return Create(ErrorSource(error))
}
