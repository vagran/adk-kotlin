package com.ast.adk.omm

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


/**
 * @param requireAllFields Require all fields to be set by mapper. Separate fields can be specified
 * as optional with OmmField.optional parameter.
 * @param annotatedOnlyFields Process only fields which are annotated by OmmField annotation.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OmmClass(
    val requireAllFields: OmmOption = OmmOption.NOT_SET,
    val annotatedOnlyFields: OmmOption = OmmOption.NOT_SET,
    val walkBaseClasses: OmmOption = OmmOption.NOT_SET,
    val requireLateinitVars: OmmOption = OmmOption.NOT_SET
)


/** Specify custom parameters for mapped field.
 * @param delegatedRepresentation The field marked with this annotation is used to fully represent
 * the class. Only one field in a class may be marked with such annotation.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class OmmField(
    val name: String = "",
    val optional: Boolean = false,
    val required: Boolean = false,
    val delegatedRepresentation: Boolean = false
)


/** Do not map this property. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class OmmIgnore
