/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.injector

import java.util.*
import kotlin.reflect.KClass

/** Builder for advanced component construction.  */
class ComponentBuilder<T: Any> internal constructor(private val rootCls: KClass<T>) {

    @Suppress("UNCHECKED_CAST")
    fun Build(): T
    {
        val dg = DependencyGraph(rootCls, modules, overrideModules)
        dg.Compile()
        return dg.CreateRoot() as T
    }

    /** Specify module instance. Must be specified for modules which do not have default
     * constructor.
     */
    fun WithModule(module: Any): ComponentBuilder<T>
    {
        modules.add(module)
        return this
    }

    /** Specify override module instance. Override module may override any providers. Its class is
     * not required to be declared in component modules list. Useful for unit testing.
     */
    fun OverrideModule(module: Any): ComponentBuilder<T>
    {
        overrideModules.add(module)
        return this
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val modules = ArrayList<Any>()
    private val overrideModules = ArrayList<Any>()
}
