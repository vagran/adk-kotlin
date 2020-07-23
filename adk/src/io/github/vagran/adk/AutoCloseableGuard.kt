package io.github.vagran.adk

import java.lang.Exception
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** Close the receiver if an exception occurs in the provided block. */
@ExperimentalContracts
inline fun <R> AutoCloseable.Guard(block: () -> R): R
{
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return try {
        block()
    } catch (e: Throwable) {
        try {
            close()
        } catch (closeError: Exception) {
            e.addSuppressed(closeError)
        }
        throw e
    }
}
