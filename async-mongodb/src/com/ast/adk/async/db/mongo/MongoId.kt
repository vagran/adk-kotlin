package com.ast.adk.async.db.mongo

/** Mark ObjectId field with this annotation. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class MongoId
