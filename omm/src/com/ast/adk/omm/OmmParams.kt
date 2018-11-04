package com.ast.adk.omm

import kotlin.reflect.KVisibility

/**
 * @param acceptedVisibility Properties of the specified or less strict visibility are processed.
 * It also applies to constructor which is used for new instance creation by a mapper.
 */
data class OmmParams(
    val requireAllFields: Boolean = false,
    val annotatedOnlyFields: Boolean = false,
    val acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    val walkBaseClasses: Boolean = true,
    val allowInnerClasses: Boolean = false,
    val requireLateinitVars: Boolean = true
)
