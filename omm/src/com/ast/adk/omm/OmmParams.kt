package com.ast.adk.omm

import kotlin.reflect.KVisibility

data class OmmParams(
    val requireAllFields: Boolean = false,
    val annotatedOnlyFields: Boolean = false,
    val acceptedVisibility: KVisibility = KVisibility.PUBLIC,
    val walkBaseClasses: Boolean = true,
    val allowInnerClasses: Boolean = false
)
