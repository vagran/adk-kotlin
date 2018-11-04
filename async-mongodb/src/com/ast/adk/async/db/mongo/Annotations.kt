package com.ast.adk.async.db.mongo

import com.ast.adk.omm.OmmOption
import kotlin.reflect.KClass

/** Annotation for mapped class fields.
 * @param name Field name to use in the database. By default is the field name in the source code.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoField(val name: String = "")//XXX

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoClass(val allowUnmatchedFields: OmmOption = OmmOption.NOT_SET,
                            val serializeNulls: OmmOption = OmmOption.NOT_SET,
                            val codec: KClass<*> = Unit::class)

/** Mark ObjectId field with this annotation. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoId
