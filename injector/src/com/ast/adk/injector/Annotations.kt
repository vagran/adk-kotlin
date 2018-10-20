package com.ast.adk.injector

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
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
        AnnotationTarget.CLASS)
annotation class Singleton


/** Declares component which contains roots of DI objects hierarchy. Should be usual injectable
 * class (with constructor and/or fields annotated by @Inject).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Component(
    /** List of modules to use in this component.  */
    val modules: Array<KClass<*>> = [])


/** Declares a module. A module should have factory methods annotated with @Provides annotation.  */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Module(
    /** Include other modules to this module definition.  */
    val include: Array<KClass<*>> = [])


/** Declares module factory method for injected instance construction.  */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY)
annotation class Provides


/** Annotate factory-constructable class constructor parameters which are specified to
 * DiFactory.Create() method.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FactoryParam
