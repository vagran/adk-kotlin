package com.ast.adk.async

typealias DeferredCallback<T> = (result: T, error: Throwable) -> Unit

@Suppress("CanBePrimaryConstructorProperty")
/** Represents deferred result (synonymous to future or promise). Result is either some successfully
 * obtained value or error.
 */
class Deferred<T> private constructor(result: T?, error: Throwable?) {

    @FunctionalInterface
    interface Callback<in T> {
        fun Invoke(result: T, error: Throwable)
    }

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
    }

    fun SetResult(result: T)
    {

    }

    fun SetError(error: Throwable)
    {

    }

    fun Subscribe(cbk: DeferredCallback<in T>)
    {

    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    /** Either result value or error. Both are null if not yet completed. */
    private var result: T? = result
    private var error: Throwable? = error

    /** Most common case is just one subscriber so do not allocate list if possible. */
    private var subscriber: DeferredCallback<in T>? = null
    /** List for a case there are more than one subscriber. */
    private var subscribers: List<DeferredCallback<in T>>? = null

    private constructor(): this(null, null)
}