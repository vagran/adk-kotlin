package com.ast.adk.json

/** Specify custom parameters for serialized field. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class JsonField(val name: String = "")

/** Do not serialize this property. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class JsonTransient
