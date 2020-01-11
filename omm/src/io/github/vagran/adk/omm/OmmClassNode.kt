/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.omm

import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

typealias OmmGetterFunc = (obj: Any) -> Any?
typealias OmmSetterFunc = (obj: Any, value: Any?) -> Unit
/**
 * Override default name selection algorithm for the specified property.
 * @return Custom name, null for default name.
 */
typealias OmmFieldNameHook = (prop: KProperty1<*, *>) -> String?

/** Represents a mapped class. */
@Suppress("UNCHECKED_CAST")
open class OmmClassNode<TFieldNode: OmmClassNode.OmmFieldNode>(val cls: KClass<*>,
                                                               val params: OmmParams) {

    val fields = HashMap<String, TFieldNode>()
    val dataCtr: KFunction<*>? = if (cls.isData) cls.primaryConstructor else null
    val defCtr: DefConstructor?
    val defCtrMissingReason: String?
    var delegatedRepresentationField: TFieldNode? = null
    val finalizers: List<KFunction<*>>

    class FieldParams(
        val property: KProperty1<*, *>,
        val fieldAnn: OmmField?,
        val requiredDefault: Boolean,
        val requireLateinitVars: Boolean,
        val index: Int,
        val dataCtrParam: KParameter?,
        val enumByName: Boolean,
        val serializeNull: Boolean
    )

    interface DefConstructor {
        /** Outer class, null if not for inner class. */
        val outerCls: KClass<*>?

        /** @param outer Outer class instance, null if not inner class. */
        fun Construct(outer: Any?): Any
    }

    /** Enum field encoding mode. */
    enum class EnumMode {
        /** Field is not enum. */
        NONE,
        /** Encode by ordinal value. */
        ORDINAL,
        /** Encode by name string. */
        NAME
    }

    open class OmmFieldNode(params: FieldParams) {
        val property = params.property
        val index = params.index
        val isRequired: Boolean
        val isNullable = property.returnType.isMarkedNullable
        val dataCtrParam = params.dataCtrParam
        val getter: OmmGetterFunc
        val setter: OmmSetterFunc?
        val enumMode: EnumMode
        val serializeNull = params.serializeNull
        val setList: Boolean
        val setMap: Boolean

        init {
            enumMode = if (property.returnType.jvmErasure.isSubclassOf(Enum::class)) {
                if (params.enumByName) {
                    EnumMode.NAME
                } else {
                    EnumMode.ORDINAL
                }
            } else {
                EnumMode.NONE
            }

            getter = { obj -> (property as KProperty1<Any, Any?>).get(obj) }
            setter =
                if (property is KMutableProperty1) {
                    { obj, value -> (property as KMutableProperty1<Any, Any?>).set(obj, value) }
                } else {
                    null
                }

            var isRequired = params.requiredDefault
            if (dataCtrParam != null) {
                isRequired = !dataCtrParam.isOptional
            }
            if (params.requireLateinitVars && property.isLateinit) {
                isRequired = true
            }
            if (params.fieldAnn != null) {
                if (params.fieldAnn.required) {
                    isRequired = true
                    if (dataCtrParam == null && setter == null) {
                        throw IllegalArgumentException(
                            "Readonly property cannot be required: $property")
                    }
                } else if (params.fieldAnn.optional) {
                    if (property.isLateinit) {
                        throw IllegalArgumentException(
                            "lateinit property cannot be optional: $property")
                    }
                    if (dataCtrParam != null && !dataCtrParam.isOptional) {
                        throw IllegalArgumentException(
                            "Required data class parameter cannot be optional: $property")
                    }
                    isRequired = false
                }
            }

            this.isRequired = isRequired && setter != null
            setList = setter == null && property.returnType.jvmErasure.isSubclassOf(MutableList::class)
            setMap = setter == null && property.returnType.jvmErasure.isSubclassOf(MutableMap::class)
        }
    }

    /**
     * @param additionalAnnotations Additional annotations to check for presence and accept the
     * field if annotatedOnlyFields option is set.
     */
    fun Initialize(params: OmmParams, fieldNodeFabric: (params: FieldParams) -> TFieldNode,
                   fieldNameHook: OmmFieldNameHook? = null,
                   additionalAnnotations: List<KClass<out Annotation>>? = null)
    {
        val clsAnn: OmmClass? = params.FindAnnotation(cls)

        val requiredDefault = clsAnn?.requireAllFields?.booleanValue ?: params.requireAllFields
        val annotatedOnlyFields = clsAnn?.annotatedOnlyFields?.booleanValue ?: params.annotatedOnlyFields
        val walkBaseClasses = clsAnn?.walkBaseClasses?.booleanValue ?: params.walkBaseClasses
        val requireLateinitVars = clsAnn?.requireLateinitVars?.booleanValue ?: params.requireLateinitVars
        val enumByName = clsAnn?.enumByName?.booleanValue ?: params.enumByName
        val serializeNulls = clsAnn?.serializeNulls?.booleanValue ?: params.serializeNulls

        val classes = if (walkBaseClasses) cls.allSuperclasses + cls else listOf(cls)

        /* First check if delegated representation field is present. */
        var drFieldPresent = false
        IterateFields(classes, params, additionalAnnotations, annotatedOnlyFields) {
            prop, _, fieldAnn ->

            if (fieldAnn != null && fieldAnn.delegatedRepresentation) {
                if (drFieldPresent) {
                    throw Error("Delegated representation field redefined in $prop")
                }
                drFieldPresent = true
            }

            return@IterateFields true
        }

        IterateFields(classes, params, additionalAnnotations, annotatedOnlyFields) {
            prop, visibility, fieldAnn ->

            if (drFieldPresent && (fieldAnn == null || !fieldAnn.delegatedRepresentation)) {
                return@IterateFields true
            }

            if (visibility > KVisibility.PUBLIC) {
                prop.isAccessible = true
            }

            val customName = fieldNameHook?.invoke(prop)
            val name = customName ?:
            if (fieldAnn != null && !fieldAnn.name.isEmpty()) {
                fieldAnn.name
            } else {
                prop.name
            }

            if (name in fields) {
                throw IllegalArgumentException("Duplicated field name: $prop")
            }

            val fieldParams = FieldParams(prop, fieldAnn, requiredDefault, requireLateinitVars,
                                          fields.size,
                                          dataCtr?.findParameterByName(prop.name),
                                          fieldAnn?.enumByName?.booleanValue ?: enumByName,
                                          fieldAnn?.serializeNull?.booleanValue ?: serializeNulls)
            val fieldNode = fieldNodeFabric(fieldParams)



            return@IterateFields if (drFieldPresent) {
                delegatedRepresentationField = fieldNode
                false
            } else {
                fields[name] = fieldNode
                true
            }
        }
    }

    abstract inner class FieldSetter {
        abstract fun Set(fieldNode: OmmFieldNode, value: Any?)
        abstract fun Finalize(): Any

        protected fun CheckValueSet(fieldNode: OmmFieldNode, value: Any?)
        {
            if (value == null && !fieldNode.isNullable) {
                throw OmmError(
                    "Attempted to set null value for non-nullable property ${fieldNode.property}")
            }
            if (setMask != null) {
                if (setMask[fieldNode.index]) {
                    throw OmmError("Duplicated field ${fieldNode.property}")
                }
                setMask[fieldNode.index] = true
            }
        }

        protected fun CheckAllSet()
        {
            if (setMask == null) {
                return
            }
            for (field in fields.values) {
                if (field.isRequired && !setMask[field.index]) {
                    throw OmmError("Required field not set: ${field.property}")
                }
            }
        }

        private val setMask: BooleanArray? =
            if (delegatedRepresentationField == null) BooleanArray(fields.size) else null
    }

    /** Begin new object construction. Returned field setter should be used to set all required
     * fields and obtain object instance.
     * @param outer Outer object instance for inner class, null otherwise.
     */
    fun SpawnObject(outer: Any?): FieldSetter
    {
        return if (dataCtr != null) {
            DataClassFieldSetter()
        } else {
            RegularClassFieldSetter(outer)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    init {
        if (dataCtr != null) {
            defCtr = null
            defCtrMissingReason = null
        } else {
            var defCtrMissingReason: String? = null
            defCtr = try {
                GetDefaultConstructor(cls, params.acceptedVisibility)
            } catch (e: IllegalArgumentException) {
                defCtrMissingReason = e.message
                null
            }
            this.defCtrMissingReason = defCtrMissingReason
        }

        val finalizers = cls.memberFunctions
            .filter { params.FindAnnotation<OmmFinalizer>(it) != null }

        finalizers.forEach {
            func ->
            func.parameters.forEachIndexed {
                idx, param ->
                if (idx != 0) {
                    if (!param.isOptional) {
                        throw Error("Non-optional argument found in finalizer method: $param")
                    }
                }
            }
        }

        if (finalizers.isEmpty()) {
            this.finalizers = emptyList()
        } else {
            this.finalizers = finalizers
        }
    }

    private class FieldValue(
        val fieldNode: OmmFieldNode,
        val value: Any?
    )

    private inner class RegularClassFieldSetter(outer: Any?): FieldSetter() {

        override fun Set(fieldNode: OmmFieldNode, value: Any?)
        {
            if (fieldNode.setter == null && !fieldNode.setList && !fieldNode.setMap) {
                throw OmmError("Attempted to set read-only field ${fieldNode.property}")
            }
            CheckValueSet(fieldNode, value)
            when {
                fieldNode.setList -> {
                    val list = fieldNode.getter(obj) as MutableList<Any>
                    list.clear()
                    list.addAll(value as List<Any>)
                }
                fieldNode.setMap -> {
                    val map = fieldNode.getter(obj) as MutableMap<Any, Any>
                    map.clear()
                    map.putAll(value as Map<Any, Any>)
                }
                else -> fieldNode.setter!!.invoke(obj, value)
            }
        }

        override fun Finalize(): Any
        {
            CheckAllSet()
            finalizers.forEach {
                it.callBy(mapOf(it.parameters[0] to obj))
            }
            return obj
        }

        private val obj: Any

        init {
            if (defCtr == null) {
                throw OmmError(
                    "Cannot instantiate class without accessible parameter-less constructor: " +
                    "$cls, $defCtrMissingReason"
                )
            }
            obj = defCtr.Construct(outer)
        }
    }

    /** Used for data class construction. */
    private inner class DataClassFieldSetter: FieldSetter() {
        override fun Set(fieldNode: OmmFieldNode, value: Any?)
        {
            CheckValueSet(fieldNode, value)
            if (fieldNode.dataCtrParam != null) {
                dataCtrParams[fieldNode.dataCtrParam] = value
            } else {
                values.add(FieldValue(fieldNode, value))
            }
        }

        override fun Finalize(): Any
        {
            CheckAllSet()
            val obj = dataCtr!!.callBy(dataCtrParams) as Any
            for (value in values) {
                if (value.fieldNode.setter == null) {
                    throw OmmError("Attempted to set read-only field ${value.fieldNode.property}")
                }
                value.fieldNode.setter.invoke(obj, value.value)
            }
            finalizers.forEach {
                it.callBy(mapOf(it.parameters[0] to obj))
            }
            return obj
        }

        private val dataCtrParams = HashMap<KParameter, Any?>(fields.size)
        private val values = ArrayList<FieldValue>(fields.size)
    }

    private fun IterateFields(classes: List<KClass<*>>,
                              params: OmmParams,
                              additionalAnnotations: List<KClass<out Annotation>>?,
                              annotatedOnlyFields: Boolean,
                              handler: (prop: KProperty1<*, *>,
                                        visibility: KVisibility,
                                        fieldAnn: OmmField?) -> Boolean)
    {
        for (curCls in classes) {
            if (curCls.isInner && !params.allowInnerClasses) {
                throw IllegalArgumentException("Inner classes not allowed: $curCls")
            }

            for (prop in curCls.declaredMemberProperties) {
                if (Modifier.isTransient(prop.javaField?.modifiers ?: 0) ||
                    params.FindAnnotation<OmmIgnore>(prop) != null) {

                    continue
                }

                val fieldAnn = params.FindAnnotation<OmmField>(prop)
                var additionalAnnFound = false
                if (additionalAnnotations != null) {
                    for (ann in additionalAnnotations) {
                        if (prop.annotations.firstOrNull { ann.isInstance(it) } != null) {
                            additionalAnnFound = true
                            break
                        }
                    }
                }
                if (fieldAnn == null && annotatedOnlyFields && !additionalAnnFound) {
                    continue
                }

                if (Modifier.isStatic(prop.javaField?.modifiers ?: 0)) {
                    if (fieldAnn != null || additionalAnnFound) {
                        throw IllegalArgumentException("Static field annotated: $prop")
                    }
                    continue
                }

                val visibility = prop.visibility
                if (visibility == null || visibility > params.acceptedVisibility) {
                    if (fieldAnn != null) {
                        throw IllegalArgumentException(
                            "Insufficient visibility for mapped field: $prop")
                    }
                    continue
                }

                if (!handler(prop, visibility, fieldAnn)) {
                    return
                }
            }
        }
    }
}

fun GetDefaultConstructor(cls: KClass<*>, visibility: KVisibility): OmmClassNode.DefConstructor
{
    if (cls.isAbstract) {
        throw IllegalArgumentException("No constructor for abstract class")
    }
    if (cls.isSealed) {
        throw IllegalArgumentException("No constructor for sealed class")
    }
    if (cls.objectInstance != null) {
        throw IllegalArgumentException("No constructor for singleton object")
    }

    for (ctr in cls.constructors) {
        val defCtr = CheckConstructor(cls, ctr) ?: continue
        val ctrVisibility = ctr.visibility
        if (ctrVisibility == null || ctrVisibility > visibility) {
            throw IllegalArgumentException("Unaccepted constructor visibility: $ctrVisibility")
        }
        if (ctrVisibility > KVisibility.PUBLIC) {
            ctr.isAccessible = true
        }
        return defCtr
    }
    throw IllegalArgumentException("Parameter-less constructor not found")
}

private fun CheckConstructor(cls: KClass<*>, ctr: KFunction<*>): OmmClassNode.DefConstructor?
{
    val isInner = cls.isInner
    for (paramIdx in 0 until ctr.parameters.size) {
        if (isInner && paramIdx == 0) {
            continue
        }
        val param = ctr.parameters[paramIdx]
        if (!param.isOptional) {
            return null
        }
    }

    if (isInner) {
        val outerParam = ctr.parameters[0]
        val outerCls = outerParam.type.jvmErasure
        if (ctr.parameters.size > 1) {
            return object: OmmClassNode.DefConstructor {
                override val outerCls: KClass<*>? = outerCls

                override fun Construct(outer: Any?): Any
                {
                    if (outer == null) {
                        throw OmmError(
                            "Outer class instance not provided for inner class constructor: $cls")
                    }
                    return ctr.callBy(mapOf(outerParam to outer)) as Any
                }
            }

        } else {
            return object: OmmClassNode.DefConstructor {
                override val outerCls: KClass<*>? = outerCls

                override fun Construct(outer: Any?): Any
                {
                    if (outer == null) {
                        throw OmmError(
                            "Outer class instance not provided for inner class constructor: $cls")
                    }
                    return ctr.call(outer) as Any
                }
            }
        }

    } else {
        if (ctr.parameters.isEmpty()) {
            return object: OmmClassNode.DefConstructor {
                override val outerCls: KClass<*>? = null

                override fun Construct(outer: Any?): Any {
                    return ctr.call() as Any
                }
            }

        } else {
            return object: OmmClassNode.DefConstructor {
                override val outerCls: KClass<*>? = null

                override fun Construct(outer: Any?): Any {
                    return ctr.callBy(emptyMap()) as Any
                }
            }
        }
    }
}
