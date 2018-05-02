package com.ast.adk.async.db.mongo

/** Annotation for mapped class fields.
 * @param name Field name to use in the database. By default is the field name in the source code.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoField(val name: String = "")
