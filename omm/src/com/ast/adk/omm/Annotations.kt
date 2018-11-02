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

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class OmmClass(
    val requireAllFields: OmmOption = OmmOption.NOT_SET,
    val annotatedOnlyFields: OmmOption = OmmOption.NOT_SET,
    val walkBaseClasses: OmmOption = OmmOption.NOT_SET
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class OmmField(
    val name: String = "",
    val optional: Boolean = false,
    val required: Boolean = false,
    val delegatedRepresentation: Boolean = false
)


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class OmmIgnore
