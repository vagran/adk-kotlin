package com.ast.adk.async.observable

import com.ast.adk.async.Deferred
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine

typealias MapOperatorFunc<T, U> = suspend (T) -> U

class MapOperator<T, U>(input: Observable<T>,
                        private val func: MapOperatorFunc<T, U>):
        ObservableOperator<U>() {

    init {
        input.Subscribe(this::OnNext)
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
}

fun <T, U> Observable<T>.Map(func: MapOperatorFunc<T, U>): Observable<U>
{
    return MapOperator(this, func).output
}
