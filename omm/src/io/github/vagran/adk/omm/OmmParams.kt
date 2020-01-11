/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.omm

import kotlin.reflect.KAnnotatedElement
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * @param acceptedVisibility Properties of the specified or less strict visibility are processed.
 * It also applies to constructor which is used for new instance creation by a mapper.
 * @param qualifier Qualifier to distinct annotations by.
 * @param qualifiedOnly Do not process annotations without the specified qualifier.
 * @param enumByName Encode enums by name string instead of ordinal value.
 * @param serializeNulls Serialize null fields in mapped classes by default.
 */
data class OmmParams(
    val requireAllFields: Boolean = false,
    val annotatedOnlyFields: Boolean = false,
    val acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    val walkBaseClasses: Boolean = true,
    val allowInnerClasses: Boolean = false,
    val requireLateinitVars: Boolean = true,
    val qualifier: String? = null,
    val qualifiedOnly: Boolean = false,
    val enumByName: Boolean = false,
    val serializeNulls: Boolean = true
) {

    init {
        if (qualifier?.isEmpty() == true) {
            throw IllegalArgumentException("Empty qualifier not allowed")
        }
        if (qualifiedOnly && qualifier == null) {
            throw IllegalArgumentException(
                "Qualifier should be specified if qualifiedOnly option is set")
        }
    }

    /** Find annotation with respect to specified qualifiers. */
    inline fun <reified T: Annotation> FindAnnotation(elem: KAnnotatedElement): T?
    {
        return FindAnnotation(T::class, elem)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T: Annotation> FindAnnotation(annCls: KClass<T>, elem: KAnnotatedElement): T?
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
            val elemQualifier = qualifierProp.get(ann as T) as String
            if (elemQualifier.isEmpty()) {
                if (nonQualified != null) {
                    throw OmmError("Duplicated non-qualified annotation ${annCls.simpleName} for $elem")
                }
                nonQualified = ann
                continue
            }
            if (qualifier == null || elemQualifier != qualifier) {
                continue
            }
            if (qualified != null) {
                throw OmmError("Duplicated qualified annotation ${annCls.simpleName}:$elemQualifier for $elem")
            }
            qualified = ann
        }

        if (qualified != null) {
            return qualified as T
        }
        if (qualifiedOnly) {
            return null
        }
        return nonQualified as T?
    }
}
