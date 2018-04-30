package com.ast.adk.async.observable

import com.ast.adk.async.Awaitable
import com.ast.adk.async.Deferred
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.suspendCoroutine

/** Each call to Await() returns next value, empty value if complete, throws error on failure.
 * Await() calls should not be called in parallel, it is intended for one client only. Create
 * several subscriptions if there are more than one client.
 */
class AwaitableSubscription<T> private constructor(observable: Observable<T>):
        Observable.Subscription, Awaitable<Observable.Value<T>> {

    companion object {
        fun <T> Create(observable: Observable<T>): AwaitableSubscription<T>
        {
            return AwaitableSubscription(observable)
        }
    }

    override fun Unsubscribe()
    {
        subscription.Unsubscribe()
    }

    override suspend fun Await(): Observable.Value<T>
    {
        return suspendCoroutine {
            cont ->
            var _pendingValue: Observable.Value<T>? = null
            var _pendingError: Throwable? = null
            var _valueProcessed: Deferred<Boolean>? = null
            synchronized(this) {
                if (activeWait != null) {
                    throw Error("Parallel waits are not allowed")
                }
                if (pendingValue == null && pendingError == null) {
                    activeWait = cont
                } else {
                    _valueProcessed = valueProcessed
                    valueProcessed = null
                    _pendingValue = pendingValue
                    pendingValue = null
                    _pendingError = pendingError
                    pendingError = null
                }
            }
            if (_valueProcessed != null) {
                if (_pendingError != null) {
                    cont.resumeWithException(_pendingError!!)
                } else {
                    cont.resume(_pendingValue!!)
                }
                _valueProcessed!!.SetResult(true)
            }
        }
    }

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        var _activeWait: Continuation<Observable.Value<T>>? = null
        val result = synchronized(this) {
            if (activeWait != null) {
                _activeWait = activeWait
                activeWait = null
                return@synchronized null
            } else {
                pendingValue = value
                pendingError = error
                valueProcessed = Deferred.Create()
                return@synchronized valueProcessed
            }
        }
        if (_activeWait != null) {
            if (error != null) {
                _activeWait!!.resumeWithException(error)
            } else {
                _activeWait!!.resume(value)
            }
        }
        return result
    }

    private val subscription: Observable.Subscription
    private var activeWait: Continuation<Observable.Value<T>>? = null
    private var pendingValue: Observable.Value<T>? = null
    private var pendingError: Throwable? = null
    private var valueProcessed: Deferred<Boolean>? = null

    init {
        subscription = observable.Subscribe(this::OnNext)
    }
}

fun <T> Observable<T>.Subscribe(): AwaitableSubscription<T>
{
    return AwaitableSubscription.Create(this)
}