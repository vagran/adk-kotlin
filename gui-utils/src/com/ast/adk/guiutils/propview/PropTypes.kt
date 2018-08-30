package com.ast.adk.guiutils.propview

/**
 * @param name Display name.
 * @param flat Display members of aggregated class without creating sub-category.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class PropItem(val name: String = "",
                          val flat: Boolean = false,
                          val readonly: Boolean = false)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class PropClass(val name: String = "")

/** Should be thrown when entered value is invalid. */
class ValidationError(msg: String, cause: Throwable? = null):
    Exception(msg, cause)

/** Property class which needs validation should implement this interface. */
interface ValidatedProperties {
    /** This method is called for all validated properties. It should throw ValidationError if new
     * value is invalid.
     * @param fieldName Field name in class (not display name).
     * @param value New value.
     */
    fun Validate(fieldName: String, value: Any?)
}
