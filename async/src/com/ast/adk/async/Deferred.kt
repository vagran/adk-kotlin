package com.ast.adk.async

import java.util.concurrent.atomic.AtomicInteger
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
                        if (error != null) {
                            if (this.error == null) {
                                this.error = error
                            } else {
                                this.error!!.addSuppressed(error)
                            }
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
                            result.SetError(error as Throwable)
                        }
                    }
                }
            }

            return Aggregator().result
        }

        /** Waiting for result. */
        private const val STATE_WAITING = 0
        /** Modifying subscribers list. */
        private const val STATE_SUBSCRIBING = 1
        /** Setting result. */
        private const val STATE_SETTING = 2
        /** Result set. */
        private const val STATE_READY = 3
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
     * available (any possible exception in the provided callback may be thrown in such case).
     */
    fun Subscribe(cbk: DeferredCallback<in T>)
    {
        while (true) {
            val curState = state.get()
            if (curState == STATE_WAITING) {
                if (!state.compareAndSet(STATE_WAITING,  STATE_SUBSCRIBING)) {
                    continue
                }
                if (subscriber == null) {
                    subscriber = cbk
                } else {
                    if (subscribers == null) {
                        subscribers = ArrayList()
                    }
                    subscribers!!.add(cbk)
                }
                state.set(STATE_WAITING)
                break
            }
            if (curState == STATE_READY) {
                cbk(result, error)
                break
            }
        }
    }

    override suspend fun Await(): T
    {
        return suspendCoroutine {
            cont ->
            Subscribe {
                result, error ->
                if (error != null) {
                    cont.resumeWithException(error)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    cont.resume(result as T)
                }
            }
        }
    }

    /** Wait until this deferred has result set. Mostly for unit testing. Note that all subscribers
     * may still be not yet invoked.
     */
    fun WaitComplete(): Deferred<T>
    {
        var complete = false
        val lock = java.lang.Object()
        Subscribe {
            _, _ ->
            synchronized(lock) {
                complete = true
                lock.notify()
            }
        }
        synchronized(lock) {
            while (!complete) {
                lock.wait()
            }
        }
        return this
    }

    /** Get result of the deferred. The deferred should be completed otherwise exception is thrown.
     */
    @Suppress("UNCHECKED_CAST")
    fun Get(): T
    {
        if (state.get() != STATE_READY) {
            throw IllegalStateException("Deferred is not yet complete")
        }
        if (error != null) {
            throw Exception("Deferred complete with error", error)
        }
        return result as T
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Either result value or error. Result may be null as well. */
    @Volatile private var result: T? = null
    @Volatile private var error: Throwable? = null
    private val state = AtomicInteger(STATE_WAITING)

    /** Most common case is just one subscriber so do not allocate list if possible. */
    @Volatile private var subscriber: DeferredCallback<in T>? = null
    /** List for a case there are more than one subscriber. */
    @Volatile private var subscribers: MutableList<DeferredCallback<in T>>? = null

    private constructor(result: T?, error: Throwable?): this()
    {
        this.result = result
        this.error = error
        state.set(STATE_READY)
    }

    private fun SetResult(result: T?, error: Throwable?)
    {
        while (true) {
            val curState = state.get()
            if (curState == STATE_READY) {
                val e = Exception("Result already set")
                if (error != null) {
                    e.addSuppressed(error)
                }
                throw e
            }
            if (curState == STATE_WAITING) {
                if (!state.compareAndSet(STATE_WAITING, STATE_SETTING)) {
                    continue
                }
                this.result = result
                this.error = error
                state.set(STATE_READY)
                break
            }
        }

        if (subscriber != null) {
            try {
                subscriber!!.invoke(result, error)
            } catch (e: Throwable) {
                System.err.println("Exception in Deferred subscriber invocation:")
                e.printStackTrace(System.err)
            }
            subscriber = null
        }
        if (subscribers != null) {
            for (cbk in subscribers!!) {
                try {
                    cbk(result, error)
                } catch (e: Throwable) {
                    System.err.println("Exception in Deferred subscriber invocation:")
                    e.printStackTrace(System.err)
                }
            }
            subscribers = null
        }
    }
}
