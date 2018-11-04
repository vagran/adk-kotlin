package com.ast.adk.json

import com.ast.adk.omm.OmmOption
import kotlin.reflect.KClass

/** @param allowUnmatchedFields Do not fail if some field in JSON cannot be matched to object
 * field, it is just silently ignored if this parameter is true.
 * @param codec Specify class which implements codec.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class JsonClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                           val serializeNulls: OmmOption = OmmOption.NOT_SET,
                           val codec: KClass<*> = Unit::class)
