/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.EnumFromString
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure


/* Encapsulates entity state. Typical use cases:
 * * Create new state with some default values and some specified values.
 * * Create state by loading it from DB.
 * * Serialize some part of the state for UI.
 * * Mutate some state values from UI.
 * * Update DB entry according to mutated values.
 * * Synchronize access.
 * * Support transactions for atomic modification of several values.
 * * Validators for some values.
 *
 * State values are categorized into parameters and internal state. Parameters are usually specified
 * on creation and later can be modified by UI. Internal state is usually maintained by the entity
 * itself.
 */
class ManagedState(private var loadFrom: Map<String, Any?>? = null,
                   val lock: ReadWriteLock? = ReentrantReadWriteLock()) {

    fun interface ValueValidator<T> {
        /** Throw exception if validation failed. */
        fun Validate(prevValue: T, newValue: T)
    }

    fun interface DefaultValueProvider<T> {
        fun Provide(): T
    }

    inner class DelegateProvider<T>(private val isId: Boolean,
                                    private val isParam: Boolean,
                                    private val defValue: DefaultValueProvider<T>? = null) {

        operator fun provideDelegate(thisRef: Any,
                                     prop: KProperty<*>): IDelegate<T>
        {

            return DelegateImpl(isId, isParam, GetDefaultValue(prop, defValue),
                                validator, infoLevel, infoGroup).also {
                values[prop.name] = it
            }
        }

        fun Validator(validator: ValueValidator<T>): DelegateProvider<T>
        {
            this.validator = validator
            return this
        }

        fun InfoLevel(infoLevel: Int): DelegateProvider<T>
        {
            this.infoLevel = infoLevel
            return this
        }

        fun InfoGroup(group: Any): DelegateProvider<T>
        {
            this.infoGroup = group
            return this
        }

        private var validator: ValueValidator<T>? = null
        private var infoLevel = 0
        private var infoGroup: Any? = null
    }

    interface IDelegate<T> {
        operator fun getValue(thisRef: Any, prop: KProperty<*>): T
        operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T)
    }

    fun <T> Id(defValue: DefaultValueProvider<T>): DelegateProvider<T>
    {
        return DelegateProvider(isId = true, isParam = false, defValue)
    }

    fun <T> Id(defValue: T): DelegateProvider<T>
    {
        return Id { defValue }
    }

    operator fun <T> invoke(): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = false)
    }

    operator fun <T> invoke(defValue: DefaultValueProvider<T>): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = false, defValue)
    }

    operator fun <T> invoke(defValue: T): DelegateProvider<T>
    {
        return invoke { defValue }
    }

    fun <T> Param(): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = true)
    }

    fun <T> Param(defValue: DefaultValueProvider<T>): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = true, defValue)
    }

    fun <T> Param(defValue: T): DelegateProvider<T>
    {
        return Param { defValue }
    }

    fun Mutate(params: Map<String, Any?>)
    {
        //XXX
    }

    /** Mutate in one transaction. Change properties as needed in the block. */
    fun <T> Mutate(block: () -> T): T
    {
        return block()
    }

    fun ViewMap(): Map<String, Any?>
    {
        //XXX
        return HashMap()
    }

    fun MutableMap(): MutableMap<String, Any?>
    {
        return HashMap()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var numLoaded = 0
    private val values = HashMap<String, DelegateImpl<*>>()

    private inner class DelegateImpl<T>(
        private val isId: Boolean,
        private val isParam: Boolean,
        private var value: T,
        private val validator: ValueValidator<T>?,
        private val infoLevel: Int,
        private val infoGroup: Any?): IDelegate<T> {

        override operator fun getValue(thisRef: Any, prop: KProperty<*>): T
        {
            //XXX transactions
            return value
        }

        override operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T)
        {
            //XXX
            TODO()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> GetDefaultValue(prop: KProperty<*>, defValue: DefaultValueProvider<T>?): T
    {
        val name = prop.name
        val isNullable = prop.returnType.isMarkedNullable
        val cls = prop.returnType.jvmErasure

        loadFrom?.also {
            loadFrom ->
            if (name in loadFrom) {
                val v = loadFrom[name]
                numLoaded++
                if (numLoaded == loadFrom.size) {
                    this.loadFrom = null
                }
                if (v == null && !isNullable) {
                    throw Error("Null value loaded for non-nullable property '$name'")
                }
                if (cls.isSubclassOf(Enum::class) && v is String) {
                    try {
                        return EnumFromString(cls, v) as T
                    } catch (e: Throwable) {
                        throw Error("Failed to convert enum value from loaded string for property '$name'", e)
                    }
                }
                if (cls.isSubclassOf(Number::class) && v is Number) {
                    return v as T
                }
                if (v != null && !v::class.isSubclassOf(cls)) {
                    throw Error("Wrong type returned for property '$name': " +
                        "${v::class.qualifiedName} is not subclass of ${cls.qualifiedName}")
                }
                return v as T
            }
        }
        if (defValue != null) {
            return defValue.Provide()
        }

        when (cls) {
            Byte::class -> return 0.toByte() as T
            Short::class -> return 0.toShort() as T
            Int::class -> return 0 as T
            Long::class -> return 0L as T
            Float::class -> return 0f as T
            Double::class -> return 0.0 as T
        }

        if (!isNullable) {
            throw Error("No value provided for non-nullable property '$name'")
        }
        return null as T
    }
}
