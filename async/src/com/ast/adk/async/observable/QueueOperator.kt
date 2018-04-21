package com.ast.adk.async.observable

import com.ast.adk.async.Deferred
import java.util.*

/**
 * @param maxSize Queue size in elements.
 * @param tailDrop Least recently queued values are dropped if output is not consumed fast enough.
 * Input is requested continuously. Otherwise the input is requested only if the queue has free
 * space available.
 */
class QueueOperator<T>(input: Observable<T>,
                       private val maxSize: Int,
                       private val tailDrop: Boolean): Observable.Source<T> {

    val output: Observable<T> = Observable.Create(this)

    init {
        input.Subscribe(this::OnNext)
    }

    override fun Get(): Deferred<Observable.Value<T>>
    {
        var _lastValue: Observable.Value<T>? = null
        var _lastError: Throwable? = null
        var _valueProcessed: Deferred<Boolean>? = null
        val _pendingResult: Deferred<Observable.Value<T>> = Deferred.Create()

        synchronized(this) {
            if (pendingResult != null) {
                throw Error("Get() called while previous request not completed")
            }
            _lastValue = queue.pollFirst()
            if (_lastValue == null) {
                _lastError = pendingError
            }

            if (_lastValue != null || _lastError != null) {
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
        var _valueProcessed: Deferred<Boolean>? = null
        var _pendingResult: Deferred<Observable.Value<T>>? = null

        synchronized(this) {
            if (pendingResult != null) {
                _pendingResult = pendingResult
                pendingResult = null

            } else {
                if (error != null) {
                    pendingError = error;
                } else {
                    if (tailDrop && queue.size >= maxSize) {
                        queue.removeFirst()
                    }
                    queue.addLast(value)
                    if (!tailDrop && queue.size >= maxSize) {
                        valueProcessed = Deferred.Create()
                        _valueProcessed = valueProcessed
                    }
                }
            }
        }

        if (_pendingResult != null) {
            if (error != null) {
                _pendingResult!!.SetError(error)
            } else {
                _pendingResult!!.SetResult(value)
            }
        }

        return _valueProcessed
    }

    private val queue: Deque<Observable.Value<T>> = ArrayDeque(maxSize)
    private var pendingResult: Deferred<Observable.Value<T>>? = null
    private var pendingError: Throwable? = null
    private var valueProcessed: Deferred<Boolean>? = null
}

fun <T> Observable<T>.Queue(maxSize: Int, tailDrop: Boolean): Observable<T>
{
    return QueueOperator(this, maxSize, tailDrop).output
}
