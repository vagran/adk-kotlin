package com.ast.adk.async

import kotlin.coroutines.experimental.*

typealias DeferredCallback<T> = (result: T?, error: Throwable?) -> Unit

@Suppress("CanBePrimaryConstructorProperty")
/** Represents deferred result (synonymous to future or promise). Result is either some successfully
 * obtained value or error.
 */
class Deferred<T> private constructor(): Awaitable<T> {

    companion object {

        fun <T> Create(): Deferred<T>
        {
            return Deferred()
        }

        fun <T> ForResult(result: T): Deferred<T>
        {
            return Deferred(result, null)
        }

        fun <T> ForError(error: Throwable): Deferred<T>
        {
            return Deferred(null, error)
        }

        fun <T> ForFunc(func: suspend () -> T): Deferred<T>
        {
            val result = Create<T>()
            func.createCoroutine(object: Continuation<T> {
                override val context: CoroutineContext = EmptyCoroutineContext

                override fun resume(value: T)
                {
                    result.SetResult(value)
                }

                override fun resumeWithException(exception: Throwable)
                {
                    result.SetError(exception)
                }
            }).resume(Unit)
            return result
        }

        /** Create deferred which completes when all the specified deferred results are completed.
         * If error occurs the first one is set to the resulted deferred.
         */
        fun When(results: Collection<Deferred<*>>): Deferred<Unit>
        {
            return When(results.iterator())
        }

        fun When(vararg results: Deferred<*>): Deferred<Unit>
        {
            return When(results.iterator())
        }

        fun When(results: Iterator<Deferred<*>>): Deferred<Unit>
        {
            class Aggregator {

                val result = Create<Unit>()

                init {
                    while (results.hasNext()) {
                        val def = results.next();
                        synchronized(this) {
                            numResults++
                        }
                        def.Subscribe(this::OnComplete)
                    }
                    synchronized(this) {
                        iterDone = true
                        CheckComplete()
                    }
                }

                private var iterDone = false
                private var numResults = 0
                private var numComplete = 0
                private var error: Throwable? = null

                @Suppress("UNUSED_PARAMETER")
                private fun OnComplete(value: Any?, error: Throwable?)
                {
                    synchronized(this) {
                        numComplete++
                        if (error != null && this.error == null) {
                            this.error = error
                        }
                        CheckComplete()
                    }
                }

                private fun CheckComplete()
                {
                    if (iterDone && numComplete == numResults) {
                        if (error == null) {
                            result.SetResult(Unit)
                        } else {
                            result.SetError(error!!)
                        }
                    }
                }
            }

            return Aggregator().result
        }
    }

    fun SetResult(result: T)
    {
        SetResult(result, null)
    }

    fun SetError(error: Throwable)
    {
        SetResult(null, error)
    }

    /**
     * Subscribe for result. The provided callback is called instantly if the result is already
     * available.
     */
    fun Subscribe(cbk: DeferredCallback<in T>)
    {
        var result: T? = null
        var error: Throwable? = null
        var isComplete = false

        synchronized(this) {
            result = this.result
            error = this.error
            isComplete = this.isComplete
            if (!isComplete) {
                if (subscriber == null) {
                    subscriber = cbk
                } else {
                    if (subscribers == null) {
                        subscribers = ArrayList()
                    }
                    subscribers!!.add(cbk)
                }
            }
        }
        if (isComplete) {
            cbk(result, error)
        }
    }

    override suspend fun Await(): T
    {
        return suspendCoroutine {
            cont ->
            Subscribe({
                result, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    cont.resume(result!!)
                }
            })
        }
    }

    /** Wait until this deferred has result set. Mostly for unit testing. */
    fun WaitComplete(): Deferred<T>
    {
        var complete = false
        val lock = java.lang.Object()
        Subscribe({
            _, _ ->
            synchronized(lock) {
                complete = true
                lock.notify()
            }
        })
        synchronized(lock) {
            while (!complete) {
                lock.wait()
            }
        }
        return this
    }

    /** Get result of the deferred. The deferred should be completed otherwise exception is thrown.
     */
    fun Get(): T?
    {
        synchronized(this) {
            if (!isComplete) {
                throw IllegalStateException("Deferred is not yet complete")
            }
            if (error != null) {
                throw Exception("Deferred complete with error", error)
            }
            return result
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Either result value or error. Result may be null as well. */
    private var result: T? = null
    private var error: Throwable? = null
    private var isComplete: Boolean = false

    /** Most common case is just one subscriber so do not allocate list if possible. */
    private var subscriber: DeferredCallback<in T>? = null
    /** List for a case there are more than one subscriber. */
    private var subscribers: MutableList<DeferredCallback<in T>>? = null

    private constructor(result: T?, error: Throwable?): this()
    {
        this.result = result
        this.error = error
        isComplete = true
    }

    private fun SetResult(result: T?, error: Throwable?)
    {
        var subscriber: DeferredCallback<in T>? = null
        var subscribers: MutableList<DeferredCallback<in T>>? = null

        synchronized(this) {
            if (isComplete) {
                throw Exception("Result already set")
            }
            isComplete = true
            this.result = result
            this.error = error
            subscriber = this.subscriber
            this.subscriber = null
            subscribers = this.subscribers
            this.subscribers = null
        }

        if (subscriber != null) {
            subscriber!!.invoke(result, error)
        }
        if (subscribers != null) {
            for (cbk in subscribers!!) {
                cbk(result, error)
            }
        }
    }
}
