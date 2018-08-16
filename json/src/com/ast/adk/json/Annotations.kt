package com.ast.adk.json

/** Specify custom parameters for serialized field. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class JsonField(val name: String = "",
                           val optional: Boolean = false,
                           val required: Boolean = false)

/** Do not serialize this property. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class JsonTransient

/** @param allowUnmatchedFields Do not fail if some field in JSON cannot be matched to object
 * field, it is just silently ignored if this parameter is true.
 * @param requireAllFields Require all fields to be set from JSON. Separate fields can be specified
 * as optional with JsonField.optional parameter.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class JsonClass(val allowUnmatchedFields: Boolean = true,
                           val requireAllFields: Boolean = false)
