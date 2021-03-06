/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo

import io.github.vagran.adk.async.Awaitable
import io.github.vagran.adk.async.Deferred
import com.mongodb.async.SingleResultCallback

class MongoCallback<T>: SingleResultCallback<T>, Awaitable<T> {

    val result: Deferred<T> = Deferred.Create()

    override fun onResult(result: T, t: Throwable?)
    {
        if (t != null) {
            this.result.SetError(t)
        } else {
            this.result.SetResult(result)
        }
    }

    override suspend fun Await(): T
    {
        return result.Await()
    }
}

inline fun <TRet> MongoCall(func: (SingleResultCallback<TRet>) -> Unit): Deferred<TRet>
{
    val cbk = MongoCallback<TRet>()
    func(cbk)
    return cbk.result
}

inline fun <TRet, TArg1> MongoCall(
    func: (TArg1, SingleResultCallback<TRet>) -> Unit,
    arg1: TArg1):
    Deferred<TRet>
{
    val cbk = MongoCallback<TRet>()
    func(arg1, cbk)
    return cbk.result
}

inline fun <TRet, TArg1, TArg2> MongoCall(
    func: (TArg1, TArg2, SingleResultCallback<TRet>) -> Unit,
    arg1: TArg1,
    arg2: TArg2):
    Deferred<TRet>
{
    val cbk = MongoCallback<TRet>()
    func(arg1, arg2, cbk)
    return cbk.result
}

inline fun <TRet, TArg1, TArg2, TArg3> MongoCall(
    func: (TArg1, TArg2, TArg3, SingleResultCallback<TRet>) -> Unit,
    arg1: TArg1,
    arg2: TArg2,
    arg3: TArg3):
    Deferred<TRet>
{
    val cbk = MongoCallback<TRet>()
    func(arg1, arg2, arg3, cbk)
    return cbk.result
}

inline fun <TRet, TArg1, TArg2, TArg3, TArg4> MongoCall(
    func: (TArg1, TArg2, TArg3, TArg4, SingleResultCallback<TRet>) -> Unit,
    arg1: TArg1,
    arg2: TArg2,
    arg3: TArg3,
    arg4: TArg4):
    Deferred<TRet>
{
    val cbk = MongoCallback<TRet>()
    func(arg1, arg2, arg3, arg4, cbk)
    return cbk.result
}
