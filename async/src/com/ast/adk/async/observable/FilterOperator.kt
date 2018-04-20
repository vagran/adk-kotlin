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
}

fun <T> Observable<T>.Filter(func: FilterOperatorFunc<T>): Observable<T>
{
    return FilterOperator(this, func).output
}
