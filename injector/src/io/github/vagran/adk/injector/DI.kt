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
         * @param params Arguments for injectable constructor which have FactoryParam annotation.
         */
        fun Create(vararg params: Any?): T
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

