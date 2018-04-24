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

    private fun OnNext(value: Observable.Value<T>, error: Throwable?): Deferred<Boolean>?
    {
        val result = NextInput()
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

    init {
        input.Subscribe(this::OnNext)
    }
}

fun <T, U> Observable<T>.Map(func: MapOperatorFunc<T, U>): Observable<U>
{
    return MapOperator(this, func).output
}
