/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.EnumFromString
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure


/** Encapsulates entity state. Typical use cases:
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
 *
 * @param fullCommit Commit handler accepts full state if true, and only changed values (with ID) if
 * false.
 */
class ManagedState(private var loadFrom: Map<String, Any?>? = null,
                   val lock: ReadWriteLock? = ReentrantReadWriteLock(),
                   private val commitHandler: ((state: Map<String, Any?>) -> Unit)? = null,
                   private val asyncCommitHandler: (suspend (state: Map<String, Any?>) -> Unit)? = null,
                   private val fullCommit: Boolean = false) {

    fun interface ValueValidator<T> {
        /** Throw exception if validation failed.
         * During state construction initial validation is invoked with the same value for previous
         * and new value arguments.
         */
        fun Validate(prevValue: T, newValue: T)
    }

    fun interface SingleValueValidator<T> {
        /** Throw exception if validation failed. */
        fun Validate(newValue: T)

        fun ToValidator(): ValueValidator<T>
        {
            return ValueValidator { _, newValue -> Validate(newValue) }
        }
    }

    fun interface DefaultValueProvider<T> {
        fun Provide(): T
    }

    class ValidationError(msg: String, cause: Throwable): Exception(msg, cause)

    inner class DelegateProvider<T>(private val isId: Boolean,
                                    private val isParam: Boolean,
                                    private val defValue: DefaultValueProvider<T>? = null) {

        operator fun provideDelegate(thisRef: Any,
                                     prop: KProperty<*>): IDelegate<T>
        {
            val value = GetDefaultValue(prop, defValue)
            validator?.Validate(value, value)
            return DelegateImpl(prop, isId, isParam, value,
                                validator, infoLevel, infoGroup).also {
                values[prop.name] = it
                if (isId) {
                    idValue?.also {
                        idValue ->
                        throw IllegalStateException(
                            "ID already specified by property '${idValue.name}', " +
                            "redefining by '${prop.name}'")
                    }
                    idValue = it
                }
            }
        }

        fun Validator(validator: ValueValidator<T>): DelegateProvider<T>
        {
            this.validator = validator
            return this
        }

        fun Validator(validator: SingleValueValidator<T>): DelegateProvider<T>
        {
            this.validator = validator.ToValidator()
            return this
        }

        /**
         * @param infoLevel Desired info level, -1 to not include on any level.
         */
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
        private var infoLevel = if (isParam || isId) 0 else -1
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
        val t = OpenTransaction()
        var commit = false
        try {
            t.Mutate(params)
            commit = true
        } finally {
            CloseTransaction(commit)
        }
    }

    suspend fun MutateAsync(params: Map<String, Any?>)
    {
        val t = OpenTransaction()
        var commit = false
        try {
            t.Mutate(params)
            commit = true
        } finally {
            CloseTransactionAsync(commit)
        }
    }

    /** Mutate in one transaction. Change properties as needed in the block. */
    fun <T> Mutate(block: () -> T): T
    {
        OpenTransaction()
        var commit = false
        try {
            val ret = block()
            commit = true
            return ret
        } finally {
            CloseTransaction(commit)
        }
    }

    suspend fun <T> MutateAsync(block: () -> T): T
    {
        OpenTransaction()
        var commit = false
        try {
            val ret = block()
            commit = true
            return ret
        } finally {
            CloseTransactionAsync(commit)
        }
    }

    /** The caller is responsible for ensuring the provided block continues in the same thread where
     * mutation is started as well as all values modifications performed in this thread.
     */
    suspend fun <T> MutateAsyncS(block: suspend () -> T): T
    {
        val t = OpenTransaction()
        var commit = false
        try {
            val ret = block()
            if (t !== transaction.get()) {
                throw IllegalStateException("Mutation block continued in other thread")
            }
            commit = true
            return ret
        } finally {
            CloseTransactionAsync(commit)
        }
    }

    fun GetInfo(level: Int = 0, group: Any? = null): Map<String, Any?>
    {
        if (level < 0) {
            throw IllegalArgumentException("Bad info level value")
        }
        return GetMap(level, group)
    }

    fun ViewMap(): Map<String, Any?>
    {
        return GetMap(-1, null)
    }

    override fun toString(): String
    {
        return GetInfo().toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var numLoaded = 0
    private val values = TreeMap<String, DelegateImpl<*>>()
    private var idValue: DelegateImpl<*>? = null
    private val transaction = ThreadLocal<Transaction?>()

    private inner class DelegateImpl<T>(
        prop: KProperty<*>,
        val isId: Boolean,
        val isParam: Boolean,
        private var value: T,
        private val validator: ValueValidator<T>?,
        val infoLevel: Int,
        val infoGroup: Any?): IDelegate<T> {

        val name = prop.name
        val isNullable = prop.returnType.isMarkedNullable
        val isMutable = !isId && prop is KMutableProperty
        val curValue get() = value
        val cls = prop.returnType.jvmErasure

        @Suppress("UNCHECKED_CAST")
        override operator fun getValue(thisRef: Any, prop: KProperty<*>): T
        {
            val t = transaction.get()
            if (t != null && name in t.newValues) {
                return t.newValues[name] as T
            }
            return value
        }

        override operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T)
        {
            val t = transaction.get() ?: throw IllegalStateException(
                "Attempt to modify property outside of transaction")
            t.Mutate(name, value)
        }

        @Suppress("UNCHECKED_CAST")
        fun Validate(newValue: Any?)
        {
            try {
                validator?.also {
                    it.Validate(value, newValue as T)
                }
            } catch(e: Throwable) {
                throw ValidationError("Property '$name' validation error: ${e.message}", e)
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun Set(newValue: Any?)
        {
            value = newValue as T
        }

        fun TransformValue(value: Any?): T
        {
            return TransformValue(name, cls, value)
        }
    }

    private inner class Transaction: AutoCloseable {
        val newValues = TreeMap<String, Any?>()
        var nestCount = 1

        override fun close()
        {}

        fun Mutate(params: Map<String, Any?>)
        {
            for ((name, value) in params) {
                Mutate(name, value)
            }
        }

        fun Mutate(name: String, value: Any?)
        {
            val d = values[name] ?: throw IllegalArgumentException("No such property: '$name'")
            if (!d.isMutable) {
                throw IllegalStateException("Attempting to change immutable property: '$name'")
            }
            if (value == null && !d.isNullable) {
                throw IllegalArgumentException("Null value assigned to non-nullable property '$name'")
            }
            newValues[name] = d.TransformValue(value)
        }

        fun GetCommitData(): Map<String, Any?>
        {
            val data = TreeMap<String, Any?>(newValues)
            if (fullCommit) {
                for ((name, value) in values) {
                    if (name !in data) {
                        data[name] = value.curValue
                    }
                }
            } else {
                idValue?.also {
                    data[it.name] = it.curValue
                }
            }
            return data
        }

        fun Apply()
        {
            for ((name, value) in newValues) {
                values[name]!!.Set(value)
            }
        }
    }

    private companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> TransformValue(name: String, cls: KClass<*>, value: Any?): T
        {
            if (cls.isSubclassOf(Enum::class) && value is String) {
                try {
                    return EnumFromString(cls, value) as T
                } catch (e: Throwable) {
                    throw IllegalArgumentException(
                        "Failed to convert enum value from string for property '$name'", e)
                }
            }
            if (cls.isSubclassOf(Number::class) && value is Number) {
                return value as T
            }
            if (value != null && !value::class.isSubclassOf(cls)) {
                throw IllegalArgumentException(
                    "Wrong type returned for property '$name': " +
                    "${value::class.qualifiedName} is not subclass of ${cls.qualifiedName}")
            }
            return value as T
        }
    }

    init {
        if (commitHandler != null && asyncCommitHandler != null) {
            throw IllegalArgumentException("Only one commit handler should be specified")
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
                return TransformValue(name, cls, v)
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

    private fun OpenTransaction(): Transaction
    {
        transaction.get()?.also {
            it.nestCount++
            return it
        }
        val t = Transaction()
        transaction.set(t)
        return t
    }

    private fun BeginCloseTransaction(commit: Boolean): Transaction?
    {
        val t = transaction.get() ?: throw IllegalStateException("No transaction in progress")
        t.nestCount--
        if (t.nestCount > 0) {
            return null
        }
        if (!commit) {
            transaction.set(null)
            t.close()
            return null
        }
        return t
    }

    private fun CloseTransaction(commit: Boolean)
    {
        if (commit && asyncCommitHandler != null) {
            throw IllegalStateException("Synchronous commit not possible")
        }

        val t = BeginCloseTransaction(commit) ?: return

        val lock = this.lock?.writeLock()
        lock?.lock()
        transaction.set(null)

        try {
            for ((name, value) in t.newValues) {
                values[name]!!.Validate(value)
            }
            commitHandler?.invoke(t.GetCommitData())
            t.Apply()
        } finally {
            lock?.unlock()
            t.close()
        }
    }

    private suspend fun CloseTransactionAsync(commit: Boolean)
    {
        val t = BeginCloseTransaction(commit) ?: return
        val lock = this.lock?.writeLock()
        lock?.lock()
        transaction.set(null)
        try {
            for ((name, value) in t.newValues) {
                values[name]!!.Validate(value)
            }
            if (asyncCommitHandler != null) {
                asyncCommitHandler.invoke(t.GetCommitData())
            } else {
                commitHandler?.invoke(t.GetCommitData())
            }
            t.Apply()
        } finally {
            lock?.unlock()
            t.close()
        }
    }

    private fun GetMap(infoLevel: Int, infoGroup: Any?): TreeMap<String, Any?>
    {
        val result = TreeMap<String, Any?>()
        val lock = this.lock?.readLock()
        lock?.lock()
        for ((name, value) in values) {
            if (infoLevel == -1 ||
                (value.infoLevel != -1 && value.infoLevel <= infoLevel &&
                 infoGroup === value.infoGroup)) {
                result[name] = value.curValue
            }
        }
        if (infoLevel != -1) {
            idValue?.also {
                idValue ->
                if (idValue.infoLevel != -1 && idValue.name !in result) {
                    result[idValue.name] = idValue.curValue
                }
            }
        }
        lock?.unlock()
        return result
    }
}
