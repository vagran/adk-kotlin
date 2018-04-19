package com.ast.adk.async.observable

import com.ast.adk.async.Deferred

/** Base class for observable operators.
 * @param T Output type.
 */
abstract class ObservableOperator<T>: Observable.Source<T> {

    val output: Observable<T> = Observable.Create(this)

    /** Set to true when value consumed from output. */
    protected var valueProcessed: Deferred<Boolean>? = null
    /** Set to true when output is complete either successfully or with error. */
    protected var isComplete = false

    final override fun Get(): Deferred<Observable.Value<T>>
    {
        var _lastValue: Observable.Value<T>? = null
        var _lastError: Throwable? = null
        var _valueProcessed: Deferred<Boolean>? = null
        val _pendingResult: Deferred<Observable.Value<T>> = Deferred.Create()

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

    protected fun SetResult(value: Observable.Value<T>?, error: Throwable?)
    {
        var _valueProcessed: Deferred<Boolean>? = null
        var _pendingResult: Deferred<Observable.Value<T>>? = null
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

    private var pendingResult: Deferred<Observable.Value<T>>? = null
    private var lastValue: Observable.Value<T>? = null
    private var lastError: Throwable? = null
}
