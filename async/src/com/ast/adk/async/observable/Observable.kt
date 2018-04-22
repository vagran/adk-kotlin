package com.ast.adk.async.observable

import com.ast.adk.async.Awaitable
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

    interface Subscriber<T> {
        fun OnNext(value: Observable.Value<T>): Deferred<Boolean>?

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
                _completion = lastValue
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

    fun Subscribe(subscriber: Subscriber<T>): Subscription
    {
        return Subscribe(subscriber.ToHandler())
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
    private var lastValue: Value<T>? = null
    /** Set if complete with an error. */
    private var error: Throwable? = null
    /** Either successful completion or error occurred. */
    private var isComplete = false
    /** Protects from deep recursion on next value processing. */
    private var nextPending = false

    /** Check if next value should be requested. Request if necessary. Should be called with the
     * lock acquired.
     */
    private fun CheckNext()
    {
        if (isComplete || !isConnected || numSubscribersPending > 0 || subscribers.size == 0) {
            return
        }
        /* Check if there are still queued subscribers for last round. */
        val s = subscribers.peekFirst()
        if (s.curRound != curRound) {
            /* Current round not yet fully processed. */
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
            lastValue = value
            this.error = error
            if (error != null || (value != null && !value.isSet)) {
                isComplete = true
            }
            if (nextPending) {
                return
            }
            nextPending = true
        }
        while (true) {
            val s = synchronized(subscribers) {
                val s = subscribers.peekFirst()
                if (s == null || s.curRound == curRound) {
                    nextPending = false
                    return
                }
                subscribers.removeFirst()
                s.curRound = curRound
                s.isPending = true
                numSubscribersPending++
                return@synchronized s
            }
            s.Invoke(lastValue, this.error)
        }
    }
}
