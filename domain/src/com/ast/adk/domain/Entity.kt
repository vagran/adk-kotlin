package com.ast.adk.domain

import kotlin.reflect.KCallable
import kotlin.reflect.KParameter

/** Use it with data class instance copy() method.
 * @param params Map with parameters to override.
 */
fun <T> MutateEntity(copyMethod: KCallable<T>, params: Map<String, Any?>): T
{
    val methodParams = copyMethod.parameters
    val args = HashMap<KParameter, Any?>(methodParams.size)
    for ((name, value) in params) {
        val param = methodParams.find { it.name == name }
            ?: throw Error("Unrecognized parameter: $name")
        args[param] = value
    }
    return copyMethod.callBy(args)
}
