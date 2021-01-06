/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.argparser

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class CliOption(
    val shortName: String = "",
    val longName: String = "",
    /** Require the option to be specified. */
    val required: Boolean = false,
    /** Require argument for boolean parameter. It can be 1/0, true/false, yes/no, on/off. */
    val booleanArg: Boolean = false,
    /** Field of integer type counts number of times the option is specified. */
    val count: Boolean = false,
    /** Description to show in help text. */
    val description: String = "",
    /** Argument name to show in help text. */
    val argName: String = "",
    /** Treat the annotated field as mapped nested options. */
    val aggregated: Boolean = false
)
