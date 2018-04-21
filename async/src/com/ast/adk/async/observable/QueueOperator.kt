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
                       private val tailDrop: Boolean):
        ObservableOperator<T>() {

    init {
        input.Subscribe(this::OnNext)
    }

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        val result = NextInput()
        ProcessNext(value, error)
        return result
    }

    private fun ProcessNext(value: Observable.Value<T>, error: Throwable?)
    {
        TODO()
    }

    private val queue: Deque<Observable.Value<T>> = ArrayDeque(maxSize)
}

fun <T> Observable<T>.Queue(maxSize: Int, tailDrop: Boolean): Observable<T>
{
    return QueueOperator(this, maxSize, tailDrop).output
}
