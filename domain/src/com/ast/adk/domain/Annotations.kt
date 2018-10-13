package com.ast.adk.domain

/**
 * Mark web service endpoint method. If the method has an argument, it is expected as JSON-encoded
 * POST body. The method should returned Deferred with the value which will be converted to JSON.
 * @param name Optional name for the endpoint. Method name is used by default.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Endpoint(val name: String = "")

/**
 * Method for retrieving entity from a repository. It may optionally accept string ID of the entity
 * which is taken from URI as next path component after the endpoint name. If ID argument is not
 * specified then entity may be retrieved from the request context (e.g. stored in session). The
 * returned value is used for further path lookup.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RepositoryEndpoint(val name: String = "")
