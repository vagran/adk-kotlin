/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain

import io.github.vagran.adk.EnumFromString
import io.github.vagran.adk.async.Deferred
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.concurrent.withLock
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
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

typealias EntityCommitHandler = suspend (state: EntityInfo) -> Unit

/**
 * @param T ID type. If the entity does not have ID field, then managed object instance is passed as
 * id argument.
 */
interface EntityDeleteHandler<T> {
    /** Called in scope of transaction to affect state. */
    fun Apply(id: T)
    /** Called on commit phase to commit deletion. */
    suspend fun Commit(id: T)
}

interface IEntity {
    val state: ManagedState
}

/** Helper class for implementing IEntity interface by aggregated state object. */
open class EntityBase(override val state: ManagedState = ManagedState()): IEntity {
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
 * @param lock Lock to use in shared control block. May be specified only for root state.
 * @param fullCommit Commit handler accepts full state if true, and only changed values (ID always
 *  included) if false.
 */
class ManagedState(private var loadFrom: EntityInfo? = null,
                   private val parent: ParentRef? = null,
                   lock: ReentrantReadWriteLock? =
                       if (parent != null) null else ReentrantReadWriteLock(),
                   commitHandler: EntityCommitHandler? = null,
                   deleteHandler: EntityDeleteHandler<*>? = null,
                   private val fullCommit: Boolean = false): IEntity {

    class ParentRef(val state: ManagedState, val propName: String)

    fun interface DefaultValueProvider<T> {
        fun Provide(): T
    }

    fun interface Factory<T> {
        fun Create(loadFrom: EntityInfo, parent: ParentRef): T
    }

    inner class DelegateProvider<T>(private val isId: Boolean,
                                    private val isParam: Boolean,
                                    private val defValue: DefaultValueProvider<T>? = null) {

        operator fun provideDelegate(thisRef: Any,
                                     prop: KProperty<*>): IDelegate<T>
        {
            managedObj = thisRef
            val value = GetInitialValue(prop, defValue, factory, elementFactory)
            val d = DelegateImpl(prop, isId, isParam, value,
                                infoLevel, infoGroup, factory, elementFactory,
                                commitHandler, deleteHandler)
            values[prop.name] = d
            if (isId) {
                idValue?.also {
                    idValue ->
                    throw IllegalStateException(
                        "ID already specified by property '${idValue.name}', " +
                        "redefining by '${prop.name}'")
                }
                if (prop is KMutableProperty) {
                    throw IllegalStateException("Mutable ID field is not allowed")
                }
                idValue = d
            }
            return d
        }

        /**
         * @param infoLevel Desired info level, -1 to not include on any level.
         */
        fun InfoLevel(infoLevel: Int): DelegateProvider<T>
        {
            this.infoLevel = infoLevel
            return this
        }

        /** @param group */
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

        fun CommitHandler(commitHandler: EntityCommitHandler): DelegateProvider<T>
        {
            this.commitHandler = commitHandler
            return this
        }

        fun DeleteHandler(deleteHandler: EntityDeleteHandler<*>): DelegateProvider<T>
        {
            this.deleteHandler = deleteHandler
            return this
        }

        private var infoLevel = if (isParam || isId) 0 else -1
        private var infoGroup: Any? = null
        private var factory: Factory<T>? = null
        private var elementFactory: Factory<*>? = null
        private var commitHandler: EntityCommitHandler? = null
        private var deleteHandler: EntityDeleteHandler<*>? = null
    }

    interface IDelegate<T> {
        operator fun getValue(thisRef: Any, prop: KProperty<*>): T
        operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T)
    }

    override val state: ManagedState get() = this

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
        return Internal()
    }

    operator fun <T> invoke(defValue: DefaultValueProvider<T>): DelegateProvider<T>
    {
        return Internal(defValue)
    }

    operator fun <T> invoke(defValue: T): DelegateProvider<T>
    {
        return Internal(defValue)
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

    fun <T> Internal(): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = false)
    }

    fun <T> Internal(defValue: DefaultValueProvider<T>): DelegateProvider<T>
    {
        return DelegateProvider(isId = false, isParam = false, defValue)
    }

    fun <T> Internal(defValue: T): DelegateProvider<T>
    {
        return Internal { defValue }
    }

    suspend fun Mutate(params: EntityInfo)
    {
        WithTransaction {
            it.Mutate(params)
        }
    }

    /** Mutate in one transaction. Change properties as needed in the block. */
    suspend fun <T> Mutate(block: () -> T): T
    {
        return WithTransaction {
            block()
        }
    }

    /** Mark field dirty so that it is included into commit data. Should be called in transaction
     * context.
     */
    fun MarkDirty(fieldName: String)
    {
        if (!controlBlock.isLocked) {
            throw IllegalStateException("Attempt to mark property dirty outside of transaction")
        }
        val t = EnsureTransaction()
        t.SetDirty(fieldName)
    }

    fun MarkDirty(field: KProperty<*>)
    {
        MarkDirty(field.name)
    }

    suspend fun Delete()
    {
        if (deleteHandler == null) {
            throw IllegalStateException(
                "Delete operation not supported due to unspecified deletion handler")
        }
        return WithTransaction {
            t ->
            ApplyDeletion()
            t.SetDelete()
        }
    }

    fun GetParentRef(fieldName: String): ParentRef
    {
        if (fieldName !in values) {
            throw Error("Field not found: $fieldName")
        }
        return ParentRef(this, fieldName)
    }

    fun GetParentRef(field: KProperty<*>): ParentRef
    {
        return ParentRef(this, field.name)
    }

    fun GetInfo(level: Int = 0, group: Any? = null): EntityInfo
    {
        return GetMap(level, group)
    }

    fun ViewMap(): EntityInfo
    {
        return GetMap(-1, null)
    }

    /** Get field representation in info structure. Useful for nested entities. */
    fun GetFieldInfoValue(fieldName: String, level: Int = 0, group: Any? = null): Any?
    {
        return WithReadLock {
            controlBlock.CheckError()
            val value = values[fieldName] ?: throw Error("Field not found: $fieldName")
            value.GetInfoValue(level, group)
        }
    }

    fun GetFieldInfoValue(field: KProperty<*>, level: Int = 0, group: Any? = null): Any?
    {
        return GetFieldInfoValue(field.name, level, group)
    }

    fun GetFieldValue(fieldName: String): Any?
    {
        val v = values[fieldName] ?: throw Error("Field not found: $fieldName")
        return v.curValue
    }

    fun GetFieldValue(field: KProperty<*>): Any?
    {
        return GetFieldValue(field.name)
    }

    override fun toString(): String
    {
        return GetInfo().toString()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var _managedObj: Any? = null
    private var numLoaded = 0
    private val values = TreeMap<String, DelegateImpl<*>>()
    private var idValue: DelegateImpl<*>? = null
    private var transaction: Transaction? = null

    private val commitHandler: EntityCommitHandler? =
        if (commitHandler != null) {
            if (parent?.state?.GetFieldCommitHandler(parent) != null) {
                throw Error("Overriding commit handler from parent")
            }
            commitHandler
        } else {
            parent?.state?.GetFieldCommitHandler(parent)
        }

    private val deleteHandler: EntityDeleteHandler<*>? =
        if (deleteHandler != null) {
            if (parent?.state?.GetFieldDeleteHandler(parent) != null) {
                throw Error("Overriding delete handler from parent")
            }
            deleteHandler
        } else {
            parent?.state?.GetFieldDeleteHandler(parent)
        }

    private val controlBlock: ControlBlock =
        if (parent != null) {
            if (lock != null) {
                throw IllegalArgumentException("Lock may only be specified for root state")
            }
            parent.state.controlBlock
        } else {
            if (lock == null) {
                throw IllegalArgumentException("Lock may not be null for root state")
            }
            ControlBlock(lock)
        }

    private var managedObj: Any
        get() {
            return _managedObj ?: throw Error("Managed object not set yet")
        }
        set(value) {
            if (_managedObj === value) {
                return
            }
            if (_managedObj != null) {
                throw Error("Managed object reference mismatch, " +
                            "possibly attempting to share one state between several objects")
            }
            _managedObj = value
        }

    private class ControlBlock(val lock: ReentrantReadWriteLock) {
        var transactionNesting = 0
        val commitQueue = ArrayDeque<CommitData>()
        var commitPending: Deferred<Unit>? = null
        val transactions = ArrayList<Transaction>()
        /** Non-null error invalidates whole the state hierarchy. It cannot be longer accessed. */
        var error: Throwable? = null

        /** Check if current thread hold lock to active transaction. */
        val isLocked: Boolean get() = lock.isWriteLockedByCurrentThread

        inner class CommitHandle(private val commitData: CommitData?,
                                 private val def: Deferred<Unit>) {
            suspend fun Wait()
            {
                if (commitData == null) {
                    def.Await()
                    return
                }
                var error: Throwable? = null
                try {
                    commitData.invoke()
                } catch (e: Throwable) {
                    error = e
                }
                synchronized(commitQueue) {
                    commitPending = null
                    if (error != null) {
                        commitQueue.addFirst(commitData)
                    }
                }
                if (error != null) {
                    def.SetError(error)
                } else {
                    def.SetResult(Unit)
                }
            }
        }

        fun RegisterTransaction(t: Transaction)
        {
            transactions.add(t)
        }

        fun StartTransaction()
        {
            transactionNesting++
        }

        fun EndTransaction(error: Throwable?)
        {
            if (error != null) {
                val oldError = this.error
                this.error = error
                if (oldError != null) {
                    error.addSuppressed(oldError)
                }
            }
            if (transactionNesting == 0) {
                throw IllegalStateException("Unmatched transaction end")
            }
            transactionNesting--
            if (transactionNesting != 0) {
                return
            }
            if (this.error == null) {
                for (t in transactions) {
                    t.Finalize()?.also {
                        synchronized(commitQueue) {
                            commitQueue.addLast(it)
                        }
                    }
                }
            }
            transactions.clear()
        }

        /** Check for pending commits. Returns handle which should be waited with lock released.
         * Returns null if no pending commits and can proceed with new transaction. Should be called
         * with lock acquired.
         */
        fun GetCommit(): CommitHandle?
        {
            synchronized(commitQueue) {
                commitPending?.also {
                    return CommitHandle(null, it)
                }
                val commit = commitQueue.pollFirst() ?: return null
                val def = Deferred.Create<Unit>()
                commitPending = def
                return CommitHandle(commit, def)
            }
        }

        fun CheckError()
        {
            error?.also {
                throw Error("Accessing object with invalid state", it)
            }
        }
    }

    private class CommitData(val data: EntityInfo, val handler: EntityCommitHandler) {
        suspend operator fun invoke()
        {
            handler(data)
        }
    }

    private inner class DelegateImpl<T>(
        prop: KProperty<*>,
        val isId: Boolean,
        val isParam: Boolean,
        private var value: T,
        val infoLevel: Int,
        val infoGroup: Any?,
        val factory: Factory<T>?,
        val elementFactory: Factory<*>?,
        val commitHandler: EntityCommitHandler?,
        val deleteHandler: EntityDeleteHandler<*>?): IDelegate<T> {

        val name = prop.name
        val isNullable = prop.returnType.isMarkedNullable
        val isMutable = !isId && prop is KMutableProperty
        val curValue get() = value
        val cls = prop.returnType.jvmErasure
        val elementCls = GetElementClass(prop)

        @Suppress("UNCHECKED_CAST")
        override operator fun getValue(thisRef: Any, prop: KProperty<*>): T
        {
            controlBlock.CheckError()
            return value
        }

        override operator fun setValue(thisRef: Any, prop: KProperty<*>, value: T)
        {
            if (!controlBlock.isLocked) {
                throw IllegalStateException("Attempt to modify property outside of transaction")
            }
            val t = EnsureTransaction()
            t.Mutate(this, value)
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
                return value.state.GetInfo(infoLevel, infoGroup)
            }
            if (elementCls != null && elementCls.isSubclassOf(IEntity::class)) {
                if (value is List<*>) {
                    if (value.size == 0) {
                        return emptyList<Any?>()
                    }
                    val result = ArrayList<Any?>()
                    for (element in value) {
                        val entity = element as? IEntity
                        result.add(entity?.state?.GetInfo(infoLevel, infoGroup) ?: element)
                    }
                    return result

                } else if (value is Map<*, *>) {
                    if (value.size == 0) {
                        return emptyMap<Any?, Any?>()
                    }
                    val result = TreeMap<Any?, Any?>()
                    for ((key, element) in value.entries) {
                        val entity = element as? IEntity
                        result[key] = (entity?.state?.GetInfo(infoLevel, infoGroup) ?: element)
                    }
                    return result
                }
            }
            return value
        }
    }

    private inner class Transaction {
        private val dirtyValues: HashSet<DelegateImpl<*>>? =
            if (commitHandler != null) HashSet() else null
        private var delete = false

        fun Mutate(params: EntityInfo)
        {
            for ((name, value) in params) {
                Mutate(name, value)
            }
        }

        fun Mutate(name: String, value: Any?)
        {
            val d = values[name] ?: throw IllegalArgumentException("No such property: '$name'")
            Mutate(d, value)
        }

        fun Mutate(d: DelegateImpl<*>, value: Any?)
        {
            if (!d.isMutable) {
                throw IllegalStateException(
                    "Attempting to change immutable property: '${d.name}'")
            }
            if (value == null && !d.isNullable) {
                throw IllegalArgumentException(
                    "Null value assigned to non-nullable property '${d.name}'")
            }
            if (dirtyValues != null) {
                dirtyValues.add(d)
            } else if (parent != null) {
                parent.state.EnsureTransaction().SetDirty(parent.propName)
            }
            d.Set(d.TransformValue(value))
        }

        fun SetDelete()
        {
            delete = true
        }

        fun SetDirty(name: String)
        {
            val d = values[name] ?: throw IllegalArgumentException("No such property: '$name'")
            if (dirtyValues != null) {
                dirtyValues.add(d)
            } else if (parent != null) {
                parent.state.EnsureTransaction().SetDirty(parent.propName)
            }
        }

        /** @return Commit data if any. May be null if no data for this transaction. */
        fun Finalize(): CommitData?
        {
            transaction = null
            if (delete) {
                return CommitData(emptyMap()) { CommitDeletion() }
            }
            if (dirtyValues == null) {
                return null
            }
            val data = TreeMap<String, Any?>()
            if (fullCommit) {
                for ((name, value) in values) {
                    data[name] = value.GetInfoValue(-1, null)
                }
            } else {
                if (dirtyValues.isEmpty()) {
                    return null
                }
                for (value in dirtyValues) {
                    data[value.name] = value.GetInfoValue(-1, null)
                }
                idValue?.also {
                    data[it.name] = it.curValue
                }
            }
            return CommitData(data, commitHandler!!)
        }
    }

    private companion object {

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

    private fun GetIdForDeletion(): Any?
    {
        idValue?.also {
            return it.curValue
        }
        return managedObj
    }

    private fun GetFieldCommitHandler(ref: ParentRef): EntityCommitHandler?
    {
        return values[ref.propName]?.commitHandler
    }

    private fun GetFieldDeleteHandler(ref: ParentRef): EntityDeleteHandler<*>?
    {
        return values[ref.propName]?.deleteHandler
    }

    @Suppress("UNCHECKED_CAST")
    private fun ApplyDeletion()
    {
        (deleteHandler as EntityDeleteHandler<Any?>).Apply(GetIdForDeletion())
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun CommitDeletion()
    {
        (deleteHandler as EntityDeleteHandler<Any?>).Commit(GetIdForDeletion())
    }

    /** Transform value if necessary from representation acceptable in EntityInfo to stored
     * representation.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> TransformValue(name: String, cls: KClass<*>, value: Any?, factory: Factory<T>?,
                           elementFactory: Factory<*>?, elementCls: KClass<*>?,
                           parentName: String = name): T
    {
        if (factory != null && value is Map<*, *>) {
            return factory.Create(value as EntityInfo, ParentRef(this, parentName))
        }
        if (elementFactory != null) {
            if (value is List<*>) {
                if (value.size == 0) {
                    return value as T
                }
                val result = ArrayList<Any?>()
                for ((idx, element) in value.withIndex()) {
                    result.add(TransformValue("$name[$idx]", elementCls!!, element,
                                              elementFactory, null, null, parentName))
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
                                                 elementFactory, null, null, parentName)
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

    /** Get initial value for the specified property. */
    @Suppress("UNCHECKED_CAST")
    private fun <T> GetInitialValue(prop: KProperty<*>, defValue: DefaultValueProvider<T>?,
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

    /** Should be called with write lock acquired. */
    private fun EnsureTransaction(): Transaction
    {
        transaction?.also { return it }
        return Transaction().also {
            transaction = it
            controlBlock.RegisterTransaction(it)
        }
    }

    /** Write lock is acquired. */
    @Suppress("UNCHECKED_CAST")
    private suspend inline fun <T> WithTransaction(block: (t: Transaction) -> T): T
    {
        val lock = controlBlock.lock.writeLock()
        var done = false
        var result: T? = null
        outer@while (true) {
            lock.lock()
            inner@while (true) {
                val commit = controlBlock.GetCommit()
                if (commit != null) {
                    lock.unlock()
                    commit.Wait()
                    continue@outer
                }
                if (done) {
                    val error = controlBlock.error
                    lock.unlock()
                    if (error != null) {
                        throw error
                    }
                    return result as T
                }
                controlBlock.CheckError()
                controlBlock.StartTransaction()
                val t = EnsureTransaction()
                val error = try {
                    result = block(t)
                    null
                } catch (error: Throwable) {
                    controlBlock.EndTransaction(error)
                    error
                }
                if (error == null) {
                    controlBlock.EndTransaction(null)
                }
                done = true
            }
        }
    }

    private fun GetMap(infoLevel: Int, infoGroup: Any?): TreeMap<String, Any?>
    {
        val result = TreeMap<String, Any?>()
        WithReadLock {
            controlBlock.CheckError()
            for ((name, value) in values) {
                if (value.CheckInfoLevel(infoLevel, infoGroup)) {
                    result[name] = value.GetInfoValue(infoLevel, infoGroup)
                }
            }
            if (infoLevel != -1) {
                idValue?.also { idValue ->
                    if (idValue.infoLevel != -1 && idValue.name !in result) {
                        result[idValue.name] = idValue.curValue
                    }
                }
            }
        }
        return result
    }

    private inline fun <T> WithReadLock(block: () -> T): T
    {
        contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
        return controlBlock.lock.readLock().withLock(block)
    }
}
