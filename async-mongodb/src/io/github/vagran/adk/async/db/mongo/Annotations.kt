package io.github.vagran.adk.async.db.mongo

import io.github.vagran.adk.omm.OmmOption
import io.github.vagran.adk.omm.OmmQualifiedAnnotation
import kotlin.reflect.KClass


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@OmmQualifiedAnnotation
annotation class MongoClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                            val codec: KClass<*> = Unit::class,
                            val qualifier: String = "")


/** Mark ObjectId field with this annotation. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoId
