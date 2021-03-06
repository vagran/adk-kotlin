/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json

import io.github.vagran.adk.omm.OmmOption
import io.github.vagran.adk.omm.OmmQualifiedAnnotation
import kotlin.reflect.KClass

/** @param allowUnmatchedFields Do not fail if some field in JSON cannot be matched to object
 * field, it is just silently ignored if this parameter is true.
 * @param codec Specify class which implements codec.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@OmmQualifiedAnnotation
annotation class JsonClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                           val codec: KClass<*> = Unit::class,
                           val qualifier: String = "")
