package io.github.vagran.adk.async.observable

import java.util.*

internal class ObservableImpl<T>(private val source: Observable.Source<T>,
                                 private var isConnected: Boolean):
    Observable<T> {

    override fun Connect()
    {
        synchronized(subscribers) {
            if (isConnected) {
                return
            }
            isConnected = true
            CheckNext()
        }
    }

    override fun Subscribe(subscriber: ObservableSubscriberFunc<T>): Observable.Subscription
    {
        var _completion: Observable.Value<T>? = null
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

    // /////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param curRound Round to fire in. Can be -1 for unsubscribed entry.
     */
    private inner class SubscriptionImpl(val handler: ObservableSubscriberFunc<T>,
                                         var curRound: Byte): Observable.Subscription {

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

        fun Invoke(value: Observable.Value<T>?, error: Throwable?)
        {
            val def = handler(value ?: Observable.Value.None(), error)
            if (curRound == (-1).toByte()) {
                return
            }
            if (def == null) {
                Resubscribe()
                return
            }
            def.Subscribe {
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
            }
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
    private var lastValue: Observable.Value<T>? = null
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

    private fun OnNext(value: Observable.Value<T>?, error: Throwable?)
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
