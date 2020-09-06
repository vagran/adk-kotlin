/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import java.io.PrintWriter
import java.io.StringWriter
import kotlin.reflect.KClass

fun Throwable.GetStackTrace(): String
{
    val sw = StringWriter()
    printStackTrace(PrintWriter(sw))
    return sw.toString()
}

fun String.SplitByWhitespace(): List<String>
{
    var result: ArrayList<String>? = null

    var lastStart = -1
    for (i in 0 until length) {
        val c = get(i)
        if (Character.isWhitespace(c)) {
            if (lastStart != -1) {
                (result ?: ArrayList<String>().also {result = it}).add(substring(lastStart, i))
                lastStart = -1
            }
        } else {
            if (lastStart == -1) {
                lastStart = i
            }
        }
    }
    if (lastStart != -1) {
        return (result ?: ArrayList(1)).also {  it.add(substring(lastStart)) }
    }
    return result ?: emptyList()
}

/** Used for string to enum conversion below. */
private enum class DummyEnum

@Suppress("UNCHECKED_CAST")
fun <T: Any> EnumFromString(enumCls: KClass<T>, value: String): T
{
    /* Cast to any enum class to succeed the cast. */
    return java.lang.Enum.valueOf(enumCls.java as Class<DummyEnum>, value) as T
}

/** Separate function to make it easily removable by ProGuard. */
fun Assert(condition: Boolean, msg: String)
{
    if (!condition) {
        throw AssertionError(msg)
    }
}

fun Assert(condition: Boolean)
{
    if (!condition) {
        throw AssertionError("Assertion failed")
    }
}
