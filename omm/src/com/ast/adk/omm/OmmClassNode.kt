package com.ast.adk.omm

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

    class FieldParams(
        val property: KProperty1<*, *>,
        val fieldAnn: OmmField?,
        val requiredDefault: Boolean,
        val requireLateinitVars: Boolean,
        val index: Int,
        val dataCtrParam: KParameter?,
        val enumByName: Boolean
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
        val clsAnn: OmmClass? = FindAnnotation(cls)

        val requiredDefault = clsAnn?.requireAllFields?.booleanValue ?: params.requireAllFields
        val annotatedOnlyFields = clsAnn?.annotatedOnlyFields?.booleanValue ?: params.annotatedOnlyFields
        val walkBaseClasses = clsAnn?.walkBaseClasses?.booleanValue ?: params.walkBaseClasses
        val requireLateinitVars = clsAnn?.requireLateinitVars?.booleanValue ?: params.requireLateinitVars
        val enumByName = clsAnn?.enumByName?.booleanValue ?: params.enumByName

        for (curCls in if (walkBaseClasses) cls.allSuperclasses + cls else listOf(cls)) {
            if (curCls.isInner && !params.allowInnerClasses) {
                throw IllegalArgumentException("Inner classes not allowed: $curCls")
            }

            for (prop in curCls.declaredMemberProperties) {
                if (Modifier.isTransient(prop.javaField?.modifiers ?: 0) ||
                    FindAnnotation<OmmIgnore>(prop) != null) {

                    continue
                }

                val fieldAnn = FindAnnotation<OmmField>(prop)
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
                                              fieldAnn?.enumByName?.booleanValue ?: enumByName)
                val fieldNode = fieldNodeFabric(fieldParams)

                if (fieldAnn != null && fieldAnn.delegatedRepresentation) {
                    delegatedRepresentationField?.also {
                        throw Error("Delegated representation field redefined in $prop")
                    }
                    delegatedRepresentationField = fieldNode
                } else {
                    fields[name] = fieldNode
                }
            }
        }

        if (delegatedRepresentationField != null) {
            fields.clear()
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
    }

    private class FieldValue(
        val fieldNode: OmmFieldNode,
        val value: Any?
    )

    private inner class RegularClassFieldSetter(outer: Any?): FieldSetter() {

        override fun Set(fieldNode: OmmFieldNode, value: Any?)
        {
            if (fieldNode.setter == null) {
                throw OmmError("Attempted to set read-only field ${fieldNode.property}")
            }
            CheckValueSet(fieldNode, value)
            fieldNode.setter.invoke(obj, value)
        }

        override fun Finalize(): Any
        {
            CheckAllSet()
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
            return obj
        }

        private val dataCtrParams = HashMap<KParameter, Any?>(fields.size)
        private val values = ArrayList<FieldValue>(fields.size)
    }

    private inline fun <reified T: Annotation> FindAnnotation(elem: KAnnotatedElement): T?
    {
        return FindAnnotation(T::class, elem)
    }

    private fun <T: Annotation> FindAnnotation(annCls: KClass<T>, elem: KAnnotatedElement): T?
    {
        val isQualified = annCls.findAnnotation<OmmQualifiedAnnotation>() != null
        if (!isQualified) {
            return elem.annotations.firstOrNull { annCls.isInstance(it) } as T?
        }

        var nonQualified: Annotation? = null
        var qualified: Annotation? = null
        for (ann in elem.annotations) {
            if (!annCls.isInstance(ann)) {
                continue
            }
            val qualifierProp = annCls.memberProperties.first { it.name == "qualifier" }
            val qualifier = qualifierProp.get(ann as T) as String
            if (qualifier.isEmpty()) {
                if (nonQualified != null) {
                    throw OmmError("Duplicated non-qualified annotation ${annCls.simpleName} for $elem")
                }
                nonQualified = ann
                continue
            }
            if (params.qualifier == null || qualifier != params.qualifier) {
                continue
            }
            if (qualified != null) {
                throw OmmError("Duplicated qualified annotation ${annCls.simpleName}:$qualifier for $elem")
            }
            qualified = ann
        }

        if (qualified != null) {
            return qualified as T
        }
        if (params.qualifiedOnly) {
            return null
        }
        return nonQualified as T?
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
