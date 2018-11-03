package com.ast.adk.omm

import java.lang.reflect.Modifier
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

typealias OmmGetterFunc = (obj: Any) -> Any?
typealias OmmSetterFunc = (obj: Any, value: Any?) -> Unit

@Suppress("UNCHECKED_CAST")
open class OmmClassNode<TFieldNode: OmmClassNode.OmmFieldNode>(val cls: KClass<*>,
                                                               params: OmmParams) {

    class FieldParams(
        val property: KProperty1<*, *>,
        val fieldAnn: OmmField?,
        val requiredDefault: Boolean,
        val index: Int,
        val dataCtrParam: KParameter?
    )

    interface DefConstructor {
        val outerCls: KClass<*>?

        fun Construct(outer: Any?): Any
    }

    open class OmmFieldNode(params: FieldParams) {
        val property = params.property
        val index = params.index
        val isRequired: Boolean
        val isNullable = property.returnType.isMarkedNullable
        val dataCtrParam = params.dataCtrParam
        val getter: OmmGetterFunc
        val setter: OmmSetterFunc?

        init {
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
            if (property.isLateinit) {
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

    fun Initialize(params: OmmParams, fieldNodeFabric: (params: FieldParams) -> TFieldNode)
    {
        val clsAnn: OmmClass? = cls.findAnnotation()

        val requiredDefault = clsAnn?.requireAllFields?.booleanValue ?: params.requireAllFields
        val annotatedOnlyFields = clsAnn?.annotatedOnlyFields?.booleanValue ?: params.annotatedOnlyFields
        val walkBaseClasses = clsAnn?.walkBaseClasses?.booleanValue ?: params.walkBaseClasses

        for (curCls in if (walkBaseClasses) cls.allSuperclasses + cls else listOf(cls)) {
            if (curCls.isInner && !params.allowInnerClasses) {
                throw IllegalArgumentException("Inner classes not allowed: $curCls")
            }

            for (prop in curCls.declaredMemberProperties) {
                if (Modifier.isTransient(prop.javaField?.modifiers ?: 0) ||
                    prop.findAnnotation<OmmIgnore>() != null) {

                    continue
                }

                val fieldAnn = prop.findAnnotation<OmmField>()
                if (fieldAnn == null && annotatedOnlyFields) {
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

                val name = if (fieldAnn != null && !fieldAnn.name.isEmpty()) {
                    fieldAnn.name
                } else {
                    prop.name
                }
                if (name in fields) {
                    throw IllegalArgumentException("Duplicated field name: $prop")
                }

                val fieldParams = FieldParams(prop, fieldAnn, requiredDefault, fields.size,
                                              dataCtr?.findParameterByName(prop.name))
                fields[name] = fieldNodeFabric(fieldParams)
            }
        }
    }

    val fields = HashMap<String, TFieldNode>()
    val dataCtr: KFunction<*>? = if (cls.isData) cls.primaryConstructor else null
    val defCtr: DefConstructor?
    val defCtrMissingReason: String?

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
                    return ctr.callBy(mapOf(outerParam to outer)) as Any
                }
            }

        } else {
            return object: OmmClassNode.DefConstructor {
                override val outerCls: KClass<*>? = outerCls

                override fun Construct(outer: Any?): Any
                {
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
