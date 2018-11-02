package com.ast.adk.omm

import java.lang.reflect.Modifier
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField

data class D(val a: Int) {
}

open class OmmClassNode<TFieldNode: OmmClassNode.OmmFieldNode> {

    class FieldParams(
        val property: KProperty1<*, *>,
        val fieldAnn: OmmField?,
        val requiredDefault: Boolean,
        val index: Int,
        val dataCtrParam: KParameter?
    )

    open class OmmFieldNode(params: FieldParams) {
        val property = params.property
        val index = params.index
        val isRequired: Boolean
        val isNullable = property.returnType.isMarkedNullable
        val dataCtrParam = params.dataCtrParam

        init {
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
                    if (dataCtrParam == null) {
                        //XXX check if writeable, throw if not
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

            this.isRequired = isRequired//XXX if writeable
        }
    }

    fun Initialize(params: OmmParams, cls: KClass<*>,
                   fieldNodeFabric: (params: FieldParams) -> TFieldNode)
    {
        val clsAnn: OmmClass? = cls.findAnnotation()

        val requiredDefault = clsAnn?.requireAllFields?.booleanValue ?: params.requireAllFields
        val annotatedOnlyFields = clsAnn?.annotatedOnlyFields?.booleanValue ?: params.annotatedOnlyFields
        val walkBaseClasses = clsAnn?.walkBaseClasses?.booleanValue ?: params.walkBaseClasses

        val dataCtr: KFunction<*>? = if (cls.isData) cls.primaryConstructor else null

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
}
