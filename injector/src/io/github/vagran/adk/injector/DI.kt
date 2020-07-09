/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector

import kotlin.reflect.KClass

class DI {
    class Exception(message: String, cause: Throwable? = null): RuntimeException(message, cause)

    /** Factory for creating some injectable type T.  */
    interface Factory<T> {

        /** Create instance of injectable type T.
         *
         * @param params Arguments for injectable constructor or provider method which have
         * FactoryParam annotation.
         */
        fun Create(vararg params: Any?): T

        /** Create instance of injectable type T in the specified scope.
         * @param scope Scope object.
         * @param params Arguments for injectable constructor or provider method which have
         * FactoryParam annotation.
         */
        fun CreateScoped(scope: Scope, vararg params: Any?): T
    }

    /** Base class for custom scopes. */
    open class Scope {
        internal fun CreateSingleton(key: Any, factory: () -> Any): Any
        {
            synchronized(singletons) {
                return singletons.computeIfAbsent(key) { factory() }
            }
        }

        private val singletons = HashMap<Any, Any>()
    }

    /** List of attributes attached to value declaration. */
    class Attributes(val list: List<Annotation>) {
        inline fun <reified T: Annotation> Get(): T?
        {
            for (attr in list) {
                if (attr is T) {
                    return attr
                }
            }
            return null
        }
    }

    companion object {
        inline fun <reified T: Any> CreateComponent(): T
        {
            return CreateComponent(T::class)
        }

        fun <T: Any> CreateComponent(cls: KClass<T>): T
        {
            return ComponentBuilder(cls).Build()
        }

        inline fun <reified T: Any> ComponentBuilder(): ComponentBuilder<T>
        {
            return ComponentBuilder(T::class)
        }

        fun <T: Any> ComponentBuilder(cls: KClass<T>): ComponentBuilder<T>
        {
            return io.github.vagran.adk.injector.ComponentBuilder(cls)
        }
    }
}

