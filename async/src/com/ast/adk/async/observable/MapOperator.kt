package com.ast.adk.async.observable

import com.ast.adk.async.Deferred
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine

typealias MapOperatorFunc<T, U> = suspend (T) -> U

class MapOperator<T, U>(input: Observable<T>,
                        private val func: MapOperatorFunc<T, U>):
        Observable.Source<U> {

    val output: Observable<U> = Observable.Create(this)

    init {
        input.Subscribe(this::OnNext)
    }

    override fun Get(): Deferred<Observable.Value<U>>
    {
        var _lastValue: Observable.Value<U>? = null
        var _lastError: Throwable? = null
        var _valueProcessed: Deferred<Boolean>? = null
        val _pendingResult: Deferred<Observable.Value<U>> = Deferred.Create()

        synchronized(this) {
            if (isComplete) {
                throw Error("Get() called after completed")
            }
            _lastValue = lastValue
            _lastError = lastError

            if (lastValue != null || lastError != null) {
                if (lastError != null) {
                    isComplete = true
                } else if (!lastValue!!.isSet) {
                    isComplete = true
                }
                lastValue = null
                lastError = null
                _valueProcessed = valueProcessed
                valueProcessed = null

            } else {
                pendingResult = _pendingResult
            }
        }
        if (_lastError != null) {
            _pendingResult.SetError(_lastError!!)
        } else if (_lastValue != null) {
            _pendingResult.SetResult(_lastValue!!)
        }
        if (_valueProcessed != null) {
            _valueProcessed!!.SetResult(true)
        }
        return _pendingResult
    }

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        val result = synchronized(this) {
            /* Operator function may complete output earlier than input is completed, so just skip
             * the rest input.
             */
            if (isComplete) {
                return null
            }
            if (valueProcessed != null) {
                throw Error("Next value provided before previous one is processed")
            }
            valueProcessed = Deferred.Create()
            return@synchronized valueProcessed
        }
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

            override fun resume(value: U)
            {
                SetResult(Observable.Value.Of(value), null)
            }

            override fun resumeWithException(exception: Throwable)
            {
                SetResult(null, exception)
            }
        }).resume(Unit)
    }

    private fun SetResult(value: Observable.Value<U>?, error: Throwable?)
    {
        var _valueProcessed: Deferred<Boolean>? = null
        var _pendingResult: Deferred<Observable.Value<U>>? = null
        synchronized(this) {
            if (pendingResult != null) {
                _pendingResult = pendingResult
                pendingResult = null
                _valueProcessed = valueProcessed
                valueProcessed = null
            } else {
                lastValue = value
                lastError = error
            }
        }
        if (_pendingResult != null) {
            if (error != null) {
                _pendingResult!!.SetError(error)
            } else {
                _pendingResult!!.SetResult(value!!)
            }
            _valueProcessed!!.SetResult(true)
        }
    }

    private var pendingResult: Deferred<Observable.Value<U>>? = null
    private var valueProcessed: Deferred<Boolean>? = null
    private var lastValue: Observable.Value<U>? = null
    private var lastError: Throwable? = null
    private var isComplete = false
}

fun <T, U> Observable<T>.Map(func: MapOperatorFunc<T, U>): Observable<U>
{
    return MapOperator(this, func).output
}
