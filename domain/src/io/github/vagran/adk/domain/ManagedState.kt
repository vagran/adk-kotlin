/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.EnumFromString
import io.github.vagran.adk.domain.ManagedState.ValueValidator
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.jvmErasure

/** Intermediate entity representation. Value can be either raw value of entity field, EntityInfo
 * of nested entity (if it implements IEntity interface) and collection (list or map) of any type
 * above.
 */
typealias EntityInfo = Map<String, Any?>

typealias EntityCommitHandler = (state: EntityInfo) -> Unit

typealias EntityAsyncCommitHandler = suspend (state: EntityInfo) -> Unit

interface IEntity {
    /** Get entity info of the specified info level and group.
     * @param level Info level, -1 to all fields.
     */
    fun GetInfo(level: Int = 0, group: Any? = null): EntityInfo
    /** Entity with all fields included. */
    fun ViewMap(): EntityInfo
}

/** Helper class for implementing IEntity interface by aggregated state object. */
open class EntityBase(protected open val state: ManagedState = ManagedState()): IEntity by state {
    override fun toString(): String
    {
        return state.toString()
    }
}

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
class ManagedState(private var loadFrom: EntityInfo? = null,
                   val lock: ReadWriteLock? = ReentrantReadWriteLock(),
                   private val commitHandler: EntityCommitHandler? = null,
                   private val asyncCommitHandler: EntityAsyncCommitHandler? = null,
                   private val fullCommit: Boolean = false): IEntity {

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

    fun interface Factory<T> {
        fun Create(loadFrom: EntityInfo): T
    }

    class ValidationError(msg: String, cause: Throwable): Exception(msg, cause)

    inner class DelegateProvider<T>(private val isId: Boolean,
                                    private val isParam: Boolean,
                                    private val defValue: DefaultValueProvider<T>? = null) {

        operator fun provideDelegate(thisRef: Any,
                                     prop: KProperty<*>): IDelegate<T>
        {
            val value = GetDefaultValue(prop, defValue, factory, elementFactory)
            validator?.Validate(value, value)
            return DelegateImpl(prop, isId, isParam, value,
                                validator, infoLevel, infoGroup, factory, elementFactory).also {
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

        /** Factory for nested type. */
        fun Factory(factory: Factory<T>): DelegateProvider<T>
        {
            this.factory = factory
            return this
        }

        /** Factory for element of a collection of nested type. */
        fun ElementFactory(elementFactory: Factory<*>): DelegateProvider<T>
        {
            this.elementFactory = elementFactory
            return this
        }

        private var validator: ValueValidator<T>? = null
        private var infoLevel = if (isParam || isId) 0 else -1
        private var infoGroup: Any? = null
        private var factory: Factory<T>? = null
        private var elementFactory: Factory<*>? = null
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

    fun Mutate(params: EntityInfo)
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

    suspend fun MutateAsync(params: EntityInfo)
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

    /** Mark field dirty so that it is included into commit data. */
    fun MarkDirty(fieldName: String)
    {
        val t = transaction.get() ?: throw IllegalStateException("No active transaction")
        t.SetDirty(fieldName)
    }

    fun MarkDirty(field: KProperty<*>)
    {
        MarkDirty(field.name)
    }

    override fun GetInfo(level: Int, group: Any?): EntityInfo
    {
        return GetMap(level, group)
    }

    override fun ViewMap(): EntityInfo
    {
        return GetMap(-1, null)
    }

    /** Get field representation in info structure. Useful for nested entities. */
    fun GetFieldInfoValue(fieldName: String, level: Int = 0, group: Any? = null): Any?
    {
        val lock = this.lock?.readLock()
        lock?.lock()
        val value = values[fieldName] ?: throw Error("Field not found: $fieldName")
        val result = value.GetInfoValue(level, group)
        lock?.unlock()
        return result
    }

    fun GetFieldInfoValue(field: KProperty<*>, level: Int = 0, group: Any? = null): Any?
    {
        return GetFieldInfoValue(field.name, level, group)
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
    /** Asynchronous commit in progress, concurrent commit is not allowed. */
    private var isCommitting = false

    private inner class DelegateImpl<T>(
        prop: KProperty<*>,
        val isId: Boolean,
        val isParam: Boolean,
        private var value: T,
        private val validator: ValueValidator<T>?,
        val infoLevel: Int,
        val infoGroup: Any?,
        val factory: Factory<T>?,
        val elementFactory: Factory<*>?): IDelegate<T> {

        val name = prop.name
        val isNullable = prop.returnType.isMarkedNullable
        val isMutable = !isId && prop is KMutableProperty
        val curValue get() = value
        val cls = prop.returnType.jvmErasure
        val elementCls = GetElementClass(prop)

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
            return TransformValue(name, cls, value, factory, elementFactory, elementCls)
        }

        /** @return True if should be included into the specified info level and group. */
        fun CheckInfoLevel(infoLevel: Int, infoGroup: Any?): Boolean
        {
            return infoLevel == -1 ||
                    (this.infoLevel != -1 && this.infoLevel <= infoLevel &&
                     infoGroup === this.infoGroup)
        }

        fun GetInfoValue(infoLevel: Int, infoGroup: Any?): Any?
        {
            val value = value ?: return null
            if (value is IEntity) {
                return value.GetInfo(infoLevel, infoGroup)
            }
            if (elementCls != null && elementCls.isSubclassOf(IEntity::class)) {
                if (value is List<*>) {
                    if (value.size == 0) {
                        return emptyList<Any?>()
                    }
                    val result = ArrayList<Any?>()
                    for (element in value) {
                        val entity = element as? IEntity
                        result.add(entity?.GetInfo(infoLevel, infoGroup) ?: element)
                    }
                    return result

                } else if (value is Map<*, *>) {
                    if (value.size == 0) {
                        return emptyMap<Any?, Any?>()
                    }
                    val result = TreeMap<Any?, Any?>()
                    for ((key, element) in value.entries) {
                        val entity = element as? IEntity
                        result[key] = (entity?.GetInfo(infoLevel, infoGroup) ?: element)
                    }
                    return result
                }
            }
            return value
        }
    }

    private inner class Transaction {
        val newValues = TreeMap<String, Any?>()
        val dirtyValues = TreeSet<String>()
        var nestCount = 1

        fun Mutate(params: EntityInfo)
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

        fun SetDirty(name: String)
        {
            dirtyValues.add(name)
        }

        fun GetCommitData(): EntityInfo
        {
            val data = TreeMap<String, Any?>(newValues)
            if (fullCommit) {
                for ((name, value) in values) {
                    if (name !in data) {
                        data[name] = value.GetInfoValue(-1, null)
                    }
                }
            } else {
                for (name in dirtyValues) {
                    if (name in newValues) {
                        continue
                    }
                    val v = values[name] ?: throw Error("Field not found: $name")
                    data[name] = v.GetInfoValue(-1, null)
                }
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
        fun <T> TransformValue(name: String, cls: KClass<*>, value: Any?, factory: Factory<T>?,
                               elementFactory: Factory<*>?, elementCls: KClass<*>?): T
        {
            if (factory != null && value is Map<*, *>) {
                return factory.Create(value as EntityInfo)
            }
            if (elementFactory != null) {
                if (value is List<*>) {
                    if (value.size == 0) {
                        return value as T
                    }
                    val result = ArrayList<Any?>()
                    for ((idx, element) in value.withIndex()) {
                        result.add(TransformValue("$name[$idx]", elementCls!!, element,
                                                  elementFactory, null, null))
                    }
                    return result as T
                }
                if (value is Map<*, *>) {
                    if (value.size == 0) {
                        return value as T
                    }
                    val result = HashMap<Any?, Any?>()
                    for ((key, element) in value.entries) {
                        result[key] = TransformValue("$name[$key]", elementCls!!, element,
                                                     elementFactory, null, null)
                    }
                    return result as T
                }
            }
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

        /** Get element type of collection type property. */
        fun GetElementClass(prop: KProperty<*>): KClass<*>?
        {
            val cls = prop.returnType.jvmErasure
            return when {
                cls.isSubclassOf(List::class) -> {
                    if (prop.returnType.arguments.size != 1) {
                        throw Error("One type argument expected for type of property $prop")
                    }
                    val type = prop.returnType.arguments[0].type ?:
                        throw Error("No element type found for list")
                    type.jvmErasure
                }

                cls.isSubclassOf(Map::class) -> {
                    if (prop.returnType.arguments.size != 2) {
                        throw Error("Two type arguments expected for type of property $prop")
                    }
                    val type = prop.returnType.arguments[1].type ?:
                        throw Error("No element type found for map")
                    type.jvmErasure
                }

                else -> null
            }
        }
    }

    init {
        if (commitHandler != null && asyncCommitHandler != null) {
            throw IllegalArgumentException("Only one commit handler should be specified")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> GetDefaultValue(prop: KProperty<*>, defValue: DefaultValueProvider<T>?,
                                    factory: Factory<T>?, elementFactory: Factory<*>?): T
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
                return TransformValue(name, cls, v, factory, elementFactory, GetElementClass(prop))
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
        }
    }

    private suspend fun CloseTransactionAsync(commit: Boolean)
    {
        val t = BeginCloseTransaction(commit) ?: return
        var lock = this.lock?.writeLock()
        lock?.lock()
        transaction.set(null)

        try {
            if (isCommitting) {
                throw IllegalStateException("Concurrent commit not allowed")
            }
            for ((name, value) in t.newValues) {
                values[name]!!.Validate(value)
            }
            if (asyncCommitHandler != null) {
                isCommitting = true
            } else {
                commitHandler?.invoke(t.GetCommitData())
                t.Apply()
            }
        } finally {
            lock?.unlock()
        }

        if (asyncCommitHandler != null) {
            lock = null
            try {
                asyncCommitHandler.invoke(t.GetCommitData())
                lock = this.lock?.writeLock()
                lock?.lock()
                t.Apply()
            } finally {
                isCommitting = false
                lock?.unlock()
            }
        }
    }

    private fun GetMap(infoLevel: Int, infoGroup: Any?): TreeMap<String, Any?>
    {
        val result = TreeMap<String, Any?>()
        val lock = this.lock?.readLock()
        lock?.lock()
        for ((name, value) in values) {
            if (value.CheckInfoLevel(infoLevel, infoGroup)) {
                result[name] = value.GetInfoValue(infoLevel, infoGroup)
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
