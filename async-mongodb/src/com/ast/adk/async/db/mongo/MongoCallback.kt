package com.ast.adk.async.db.mongo

import com.ast.adk.async.Awaitable
import com.ast.adk.async.Deferred
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
