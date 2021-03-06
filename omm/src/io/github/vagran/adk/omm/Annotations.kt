/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.omm

enum class OmmOption {
    YES,
    NO,
    /** Take default value from mapper parameters. */
    NOT_SET;

    val booleanValue: Boolean?
        get() = when (this) {
            YES -> true
            NO -> false
            NOT_SET -> null
        }
}


/** Annotations annotated with this annotation have qualifier parameter which allows distinctions
 * of parameters for different mappers. They allowed to be repeated only with different qualifiers.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class OmmQualifiedAnnotation


/**
 * @param requireAllFields Require all fields to be set by mapper. Separate fields can be specified
 * as optional with OmmField.optional parameter.
 * @param annotatedOnlyFields Process only fields which are annotated by OmmField annotation.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@Repeatable
@OmmQualifiedAnnotation
annotation class OmmClass (
    val requireAllFields: OmmOption = OmmOption.NOT_SET,
    val annotatedOnlyFields: OmmOption = OmmOption.NOT_SET,
    val walkBaseClasses: OmmOption = OmmOption.NOT_SET,
    val requireLateinitVars: OmmOption = OmmOption.NOT_SET,
    val enumByName: OmmOption = OmmOption.NOT_SET,
    val serializeNulls: OmmOption = OmmOption.NOT_SET,
    val qualifier: String = ""
)


/** Specify custom parameters for mapped field.
 * @param delegatedRepresentation The field marked with this annotation is used to fully represent
 * the class. Only one field in a class may be marked with such annotation.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
@Repeatable
@OmmQualifiedAnnotation
annotation class OmmField(
    val name: String = "",
    val optional: Boolean = false,
    val required: Boolean = false,
    val delegatedRepresentation: Boolean = false,
    val enumByName: OmmOption = OmmOption.NOT_SET,
    val serializeNull: OmmOption = OmmOption.NOT_SET,
    val qualifier: String = ""
)


/** Do not map this property. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
@Repeatable
@OmmQualifiedAnnotation
annotation class OmmIgnore(
    val qualifier: String = ""
)


/** Mark method as decoding finalizer. It is called after the class instance is fully deserialized.
 * The method should not have any arguments (arguments with default values allowed). Multiple
 * methods can be matched, they all are called in declaration order.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
@Repeatable
@OmmQualifiedAnnotation
annotation class OmmFinalizer(
    val qualifier: String = ""
)
