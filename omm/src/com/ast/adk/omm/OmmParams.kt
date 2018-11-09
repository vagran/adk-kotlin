package com.ast.adk.omm

import kotlin.reflect.KVisibility

/**
 * @param acceptedVisibility Properties of the specified or less strict visibility are processed.
 * It also applies to constructor which is used for new instance creation by a mapper.
 * @param qualifier Qualifier to distinct annotations by.
 * @param qualifiedOnly Do not process annotations without the specified qualifier.
 */
data class OmmParams(
    val requireAllFields: Boolean = false,
    val annotatedOnlyFields: Boolean = false,
    val acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    val walkBaseClasses: Boolean = true,
    val allowInnerClasses: Boolean = false,
    val requireLateinitVars: Boolean = true,
    val qualifier: String? = null,
    val qualifiedOnly: Boolean = false
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
}
