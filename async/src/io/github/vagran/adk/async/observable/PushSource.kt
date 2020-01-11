package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred

/** Adapter for upstream-driven sources. Value can be pushed at any time. It is discarded if
 * previous value was not yet consumed by downstream so it is recommended to use in conjunction
 * with queue operator.
 */
class PushSource<T>: Observable.Source<T> {

    override fun Get(): Deferred<Observable.Value<T>>
    {
        return synchronized(this) {
            if (pendingRequest != null) {
                throw Error("Next value requested before previous request processed")
            }
            if (isComplete) {
                throw Error("Next value requested after completion")
            }
            if (pendingValue != null) {
                val value = pendingValue!!
                pendingValue = null
                if (!value.isSet) {
                    isComplete = true
                }
                return@synchronized Deferred.ForResult(value)
            }
            if (pendingError != null) {
                isComplete = true
                return@synchronized Deferred.ForError(pendingError!!)
            }
            pendingRequest = Deferred.Create()
            return@synchronized pendingRequest!!
        }
    }

    fun Push(value: T)
    {
        synchronized(this) {
            if (isComplete) {
                throw Error("Push after completed")
            }
            if (pendingRequest != null) {
                val res = pendingRequest
                pendingRequest = null
                return@synchronized res
            }
            pendingValue = Observable.Value.Of(value)
            return@synchronized null
        }?.SetResult(Observable.Value.Of(value))
    }

    fun Complete()
    {
        synchronized(this) {
            if (isComplete) {
                throw Error("Repeated completion")
            }
            if (pendingRequest != null) {
                val res = pendingRequest
                pendingRequest = null
                return@synchronized res
            }
            pendingValue = Observable.Value.None()
            return@synchronized null
        }?.SetResult(Observable.Value.None())
    }

    fun Error(error: Throwable)
    {
        synchronized(this) {
            if (isComplete) {
                throw Error("Error after completed")
            }
            if (pendingRequest != null) {
                val res = pendingRequest
                pendingRequest = null
                return@synchronized res
            }
            pendingError = error
            return@synchronized null
        }?.SetError(error)
    }

    private var pendingValue: Observable.Value<T>? = null
    private var pendingError: Throwable? = null
    private var isComplete: Boolean = false
    private var pendingRequest: Deferred<Observable.Value<T>>? = null
}
