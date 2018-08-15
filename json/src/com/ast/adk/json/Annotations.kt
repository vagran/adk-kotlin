package com.ast.adk.json

/** Specify custom name for serialized field. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class JsonName(val name: String)
