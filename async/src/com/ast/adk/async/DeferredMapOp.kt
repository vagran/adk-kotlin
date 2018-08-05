package com.ast.adk.async

typealias DeferredMapFunc<T, U> = (result: T) -> U
typealias DeferredMapErrorFunc<T, U> = (result: T?, error: Throwable?) -> U

fun <T, U> Deferred<T>.Map(mapFunc: DeferredMapFunc<T, U>): Deferred<U>
{
    val mappedDef = Deferred.Create<U>()
    Subscribe {
        result: T?, error: Throwable? ->

        if (error != null) {
            mappedDef.SetError(error)
            return@Subscribe
        }
        try {
            @Suppress("UNCHECKED_CAST")
            mappedDef.SetResult(mapFunc(result as T))
        } catch (e: Throwable) {
            mappedDef.SetError(e)
        }
    }
    return mappedDef
}

fun <T, U> Deferred<T>.Map(mapFunc: DeferredMapErrorFunc<T, U>): Deferred<U>
{
    val mappedDef = Deferred.Create<U>()
    Subscribe {
        result: T?, error: Throwable? ->

        try {
            @Suppress("UNCHECKED_CAST")
            mappedDef.SetResult(mapFunc(result as T, error))
        } catch (e: Throwable) {
            mappedDef.SetError(e)
        }
    }
    return mappedDef
}
