package com.ast.adk.async.observable

import com.ast.adk.async.Deferred
import kotlin.coroutines.experimental.Continuation
import kotlin.coroutines.experimental.CoroutineContext
import kotlin.coroutines.experimental.EmptyCoroutineContext
import kotlin.coroutines.experimental.createCoroutine

typealias FilterOperatorFunc<T> = suspend (T) -> Boolean

class FilterOperator<T>(input: Observable<T>,
                        private val func: FilterOperatorFunc<T>):
        ObservableOperator<T>() {

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
        }.createCoroutine(object : Continuation<Boolean> {

            override val context: CoroutineContext = EmptyCoroutineContext

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun resume(isPassed: Boolean)
            {
                if (isPassed) {
                    SetResult(value, null)
                } else {
                    ValueProcessed()
                }
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

fun <T> Observable<T>.Filter(func: FilterOperatorFunc<T>): Observable<T>
{
    return FilterOperator(this, func).output
}
