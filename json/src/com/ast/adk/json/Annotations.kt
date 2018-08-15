package com.ast.adk.json

/** Specify custom parameters for serialized field. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class JsonField(val name: String = "")
