/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred
import io.github.vagran.adk.async.observable.Observable.Source

/** @see Observable.Source */
typealias ObservableSourceFunc<T> = () -> Deferred<Observable.Value<T>>

typealias ObservableSuspendSourceFunc<T> = suspend () -> Observable.Value<T>

/** Subscription is cancelled if the handler throws exception or deferred error returned.
 * @param value Next value or empty value if is completed.
 * @param error Error if any. Error completes the stream.
 * @return True or null to continue subscription, false to unsubscribe.
 */
typealias ObservableSubscriberFunc<T> =
        (value: Observable.Value<T>, error: Throwable?) -> Deferred<Boolean>?

typealias ObservableSubscriberSuspendFunc<T> =
    suspend (value: Observable.Value<T>, error: Throwable?) -> Boolean

/** The same as ObservableSubscriberFunc but without return value (assuming always continue
 * subscription).
 */
typealias ObservableSubscriberVoidFunc<T> =
    (value: Observable.Value<T>, error: Throwable?) -> Unit

fun <T> ObservableSuspendSourceFunc<T>.ToSourceFunc(): ObservableSourceFunc<T>
{
    return { Deferred.ForFunc { this() } }
}

fun <T> ObservableSubscriberSuspendFunc<T>.ToSubscriberFunc(): ObservableSubscriberFunc<T>
{
    return {
        value, error ->
        Deferred.ForFunc { this(value, error) }
    }
}

/** Propagates sequence of data items. Can be used to organize data streams, events, etc. */
interface Observable<out T> {

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

    fun interface Source<T> {
        /**
         * Invoked in arbitrary thread to get next value. Next value is requested only after a
         * previous request has been completed.
         *
         * @return Deferred with next value. Empty value if no more data. Error is propagated to
         *      subscribers.
         */
        fun Get(): Deferred<Value<T>>

        companion object {
            fun <T> FromFunc(func: ObservableSourceFunc<T>): Source<T>
            {
                return Source { func() }
            }

            fun <T> FromFunc(func: ObservableSuspendSourceFunc<T>): Source<T>
            {
                return FromFunc(func.ToSourceFunc())
            }
        }
    }

    companion object {
        fun <T> Create(source: Source<T>, isConnected: Boolean = true): Observable<T>
        {
            return ObservableImpl(source, isConnected)
        }

        fun <T> Create(source: ObservableSourceFunc<T>, isConnected: Boolean = true): Observable<T>
        {
            return ObservableImpl(Source.FromFunc(source), isConnected)
        }

        fun <T> Create(source: ObservableSuspendSourceFunc<T>, isConnected: Boolean = true): Observable<T>
        {
            return ObservableImpl(Source.FromFunc(source), isConnected)
        }
    }

    interface Subscriber<in T> {
        fun OnNext(value: Value<T>): Deferred<Boolean>?

        fun OnComplete()

        fun OnError(error: Throwable)

        fun ToHandler(): ObservableSubscriberFunc<T>
        {
            return handler@ {
                value, error ->
                if (error != null) {
                    OnError(error)
                    return@handler null
                }
                if (!value.isSet) {
                    OnComplete()
                    return@handler null
                }
                return@handler OnNext(value)
            }
        }
    }

    fun interface Subscription {
        fun Unsubscribe()
    }

    /** Connect observable if it was created initially unconnected. No effect if already connected.
     */
    fun Connect()

    fun Subscribe(subscriber: ObservableSubscriberFunc<T>): Subscription

    fun Subscribe(subscriber: Subscriber<T>): Subscription
    {
        return Subscribe(subscriber.ToHandler())
    }

    fun SubscribeVoid(subscriber: ObservableSubscriberVoidFunc<T>): Subscription
    {
        return Subscribe {
            value, error ->
            subscriber(value, error)
            null
        }
    }

    fun SubscribeSuspend(subscriber: ObservableSubscriberSuspendFunc<T>): Subscription
    {
        return Subscribe(subscriber.ToSubscriberFunc())
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private class EmptyValue<out T>: Value<T> {

        override val isSet: Boolean
            get() = false

        override val value: T
            get() = throw Exception("Value is not set")

        override fun toString(): String = "None"
    }

    private class PresentValue<out T>(override val value: T): Value<T> {

        override val isSet: Boolean
            get() = true

        override fun toString(): String = value.toString()
    }
}
