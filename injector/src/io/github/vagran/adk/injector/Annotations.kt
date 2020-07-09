/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector

import kotlin.reflect.KClass


@Target(AnnotationTarget.CONSTRUCTOR,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Inject


@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Qualifier


@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(
    /** The name.  */
    val value: String = "")


/** Mark the type as singleton. Can be applied either to provider method or class itself.  */
@Target(AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Singleton(
    /** One instance per scope when true, one global instance if false. */
    val perScope: Boolean = false
)


/** Declares component which contains roots of DI objects hierarchy. Should be usual injectable
 * class (with constructor and/or fields annotated by @Inject).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Component(
    /** List of modules to use in this component.  */
    val modules: Array<KClass<*>> = [])


/** Declares a module. A module should have factory methods annotated with @Provides annotation.  */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Module(
    /** Include other modules to this module definition.  */
    val include: Array<KClass<*>> = [])


/** Declares module factory method for injected instance construction.  */
@Target(AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Provides


/** Annotate factory-constructable class constructor parameters which are specified to
 * DiFactory.Create() method.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class FactoryParam


/** Annotate user-defined attributes which are used to pass attributes to the provider method or
 * constructor.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Attribute
