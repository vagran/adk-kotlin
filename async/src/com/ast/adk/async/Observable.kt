package com.ast.adk.async

import javax.xml.transform.Templates

typealias ObservableSourceFunc<T> = () -> Deferred<Observable.Value<T>>?

class Observable<T> {

    interface Value<out T> {

        companion object {
            fun <T> None(): Value<T>
            {
                return EmptyValue()
            }

            fun <T> Of(value: T): Value<T>
            {
                return PresentValue(value)
            }
        }

        val isSet: Boolean
        val value: T
    }

    private class EmptyValue<out T>: Value<T> {

        override val isSet: Boolean
            get() = false

        override val value: T
            get() = throw Exception("Value is not set")
    }

    private class PresentValue<out T>(override val value: T): Value<T> {

        override val isSet: Boolean
            get() = true
    }

    interface Source<T> {
        /**
         * Invoked in arbitrary thread to get next value. Next value is requested only after a
         * previous request has been completed.
         *
         * @return Deferred with next value. Empty value if no more data. Null can be returned
         *      instantly instead of deferred with null value. Error is propagated to subscribers.
         */
        fun Get(): Deferred<Observable.Value<T>>?

        companion object {
            fun <T> FromFunc(func: ObservableSourceFunc<T>): Source<T>
            {
                return object: Source<T> {
                    override fun Get(): Deferred<Value<T>>?
                    {
                        return func();
                    }
                }
            }
        }
    }
}
