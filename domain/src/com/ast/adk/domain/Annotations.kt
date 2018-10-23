package com.ast.adk.domain

import kotlin.reflect.KClass

/**
 * Mark web service endpoint method. If the method has an argument, it is expected as JSON-encoded
 * POST body. Also it optionally may accept HttpRequestContext argument with the request context
 * data.
 * The method has several options for return value:
 *  1. Just return the result synchronously.
 *  2. Return the result asynchronously in suspend method.
 *  3. Provide the result asynchronously by returning Deferred with the result value.
 * In all cases the result is encoded in JSON for returning in the response body.
 * Throwing an exception causes to return JSON-encoded error with 500 HTTP status code. Other code
 * may be specified in HttpError exception class.
 *
 * @param name Optional name for the endpoint. Method name is used by default.
 * @param isRepository Marks method for retrieving entity from a repository. It may optionally
 * accept string ID of the entity which is taken from URI as next path component after the endpoint
 * name. If ID argument is not specified then entity may be retrieved from the request context (e.g.
 * stored in session). The returned value is used for further path lookup. ID may have other than
 * string type, converter method should be specified in such case by RepositoryIdConverter
 * annotation.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Endpoint(val name: String = "",
                          val isRepository: Boolean = false)


/** Converter for entity ID. The method should accept string as its argument and return type which
 * is used as ID in repository endpoints for the specified entity class.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RepositoryIdConverter(val entityClass: KClass<*>)
