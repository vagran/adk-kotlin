package com.ast.adk.async.observable

import com.ast.adk.async.Deferred
import java.util.*

/** @see Observable.Source */
typealias ObservableSourceFunc<T> = () -> Deferred<Observable.Value<T>>

/** Subscription is cancelled if the handler throws exception or deferred error returned.
 * @param value Next value or empty value if is completed.
 * @param error Error if any. Error completes the stream.
 * @return True or null to continue subscription, false to unsubscribe.
 */
typealias ObservableSubscriberFunc<T> =
        (value: Observable.Value<T>, error: Throwable?) -> Deferred<Boolean>?

/** Propagates sequence of data items. Can be used to organize data streams, events, etc. */
class Observable<T>
    private constructor (val source: Source<T>, var isConnected: Boolean) {

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

    interface Source<T> {
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
                return object: Source<T> {
                    override fun Get(): Deferred<Value<T>>
                    {
                        return func()
                    }
                }
            }
        }
    }

    companion object {
        fun <T> Create(source: Source<T>, isConnected: Boolean = true): Observable<T>
        {
            return Observable(source, isConnected)
        }

        fun <T> Create(source: ObservableSourceFunc<T>, isConnected: Boolean = true): Observable<T>
        {
            return Observable(Source.FromFunc(source), isConnected)
        }
    }

    interface Subscription {
        fun Unsubscribe()
    }

    /** Connect observable if it was created initially unconnected. No effect if already connected.
     */
    fun Connect()
    {
        synchronized(subscribers) {
            if (isConnected) {
                return
            }
            isConnected = true
            CheckNext()
        }
    }

    fun Subscribe(subscriber: ObservableSubscriberFunc<T>): Subscription
    {
        var _completion: Value<T>? = null
        var _error: Throwable? = null
        val s = synchronized(subscribers) {
            if (isComplete) {
                _completion = completion
                _error = error
                return@synchronized SubscriptionImpl(subscriber, -1)
            }
            val s = SubscriptionImpl(subscriber, curRound)
            subscribers.addLast(s)
            CheckNext()
            s
        }
        if (_completion != null || _error != null) {
            s.Invoke(_completion, _error)
        }
        return s
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

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

    /**
     * @param curRound Round to fire in. Can be -1 for unsubscribed entry.
     */
    private inner class SubscriptionImpl(val handler: ObservableSubscriberFunc<T>,
                                         var curRound: Byte): Subscription {

        override fun Unsubscribe()
        {
            synchronized(subscribers) {
                if (curRound == (-1).toByte()) {
                    return
                }
                if (isPending) {
                    numSubscribersPending--
                    isPending = false
                    CheckNext()
                } else {
                    subscribers.remove(this)
                }
                curRound = -1
            }
        }

        fun Invoke(value: Value<T>?, error: Throwable?)
        {
            val def = handler(value ?: Value.None(), error)
            if (curRound == (-1).toByte()) {
                return
            }
            if (def == null) {
                Resubscribe()
                return
            }
            def.Subscribe({
                result, _error ->
                if (_error != null) {
                    //XXX propagate somewhere
                    Unsubscribe()
                    return@Subscribe
                }
                if (result!!) {
                    Resubscribe()
                } else {
                    Unsubscribe()
                }
            })
        }

        private fun Resubscribe()
        {
            synchronized(subscribers) {
                isPending = false
                numSubscribersPending--
                if (!isComplete) {
                    subscribers.addLast(this)
                    CheckNext()
                }
            }
        }

        /** Item processing in progress. */
        var isPending = false
    }

    private val subscribers: Deque<SubscriptionImpl> = ArrayDeque(2)
    private var curRound: Byte = 0
    /** Number of subscribers currently processing a round value. */
    private var numSubscribersPending = 0;
    /** Completion value if completed successfully. */
    private var completion: Value<T>? = null
    /** Set if complete with an error. */
    private var error: Throwable? = null

    /** Check if next value should be requested. Request if necessary. Should be called with the
     * lock acquired.
     */
    //XXX recursion protection
    private fun CheckNext()
    {
        if (!isConnected || numSubscribersPending > 0 || subscribers.size == 0) {
            return
        }
        try {
            source.Get().Subscribe(this::OnNext)
        } catch (error: Throwable) {
            OnNext(null, error)
        }
    }

    private fun OnNext(value: Value<T>?, error: Throwable?)
    {
        synchronized(subscribers) {
            curRound = (1 - curRound).toByte()
            if (error != null) {
                this.error = error
            } else if (!value!!.isSet) {
                completion = value
            }
        }
        while (true) {
            val s = synchronized(subscribers) {
                val s = subscribers.peekFirst()
                if (s == null || s.curRound == curRound) {
                    return@synchronized null
                }
                subscribers.removeFirst()
                s.curRound = curRound
                s.isPending = true
                numSubscribersPending++
                return@synchronized s
            }
            if (s == null) {
                return
            }
            s.Invoke(value, error)
        }
    }

    private inline val isComplete: Boolean
        get()
        {
            return completion != null || error != null
        }
}
