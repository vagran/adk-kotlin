package com.ast.adk.async.db.mongo

import com.ast.adk.omm.OmmOption
import kotlin.reflect.KClass


@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                            val serializeNulls: OmmOption = OmmOption.NOT_SET,
                            val codec: KClass<*> = Unit::class)


/** Mark ObjectId field with this annotation. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoId
