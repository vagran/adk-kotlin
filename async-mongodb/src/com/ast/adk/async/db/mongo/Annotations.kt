package com.ast.adk.async.db.mongo

import com.ast.adk.omm.OmmOption
import com.ast.adk.omm.OmmQualifiedAnnotation
import kotlin.reflect.KClass


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
@OmmQualifiedAnnotation
annotation class MongoClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                            val codec: KClass<*> = Unit::class,
                            val qualifier: String = "")


/** Mark ObjectId field with this annotation. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoId
