/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector

import kotlin.reflect.KClass

class DI {
    class Exception(message: String, cause: Throwable? = null): RuntimeException(message, cause)

    /** Factory for creating some injectable type T.  */
    interface Factory<T> {

        /** Create instance of injectable type T. Can have scope bound.
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
                /* Factory can create other singletons when called. */
                val obj = singletons[key]
                if (obj != null) {
                    return obj
                }
                val newObj = factory()
                if (singletons.putIfAbsent(key, newObj) != null) {
                    throw Error("Recursive singleton instantiation")
                }
                return newObj
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

    /** Represents whole the dependency graph and provides possibility to dynamically create any
     * instantiate any its class.
     */
    interface Graph {
        fun <T: Any> GetFactory(cls: KClass<T>): Factory<T>

        fun <T: Any> Create(cls: KClass<T>, vararg params: Any?): T
        {
            return GetFactory(cls).Create(*params)
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

inline fun <reified T: Any> DI.Graph.GetFactory(): DI.Factory<T>
{
    return GetFactory(T::class)
}

inline fun <reified T: Any> DI.Graph.Create(vararg params: Any?): T
{
    return Create(T::class, *params)
}
