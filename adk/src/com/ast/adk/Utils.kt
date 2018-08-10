package com.ast.adk

import java.io.PrintWriter
import java.io.StringWriter

fun Throwable.GetStackTrace(): String
{
    val sw = StringWriter()
    printStackTrace(PrintWriter(sw))
    return sw.toString()
}
