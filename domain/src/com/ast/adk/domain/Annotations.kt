package com.ast.adk.domain

/**
 * Mark web service endpoint method. If the method has an argument, it is expected as JSON-encoded
 * POST body. The method should returned Deferred with the value which will be converted to JSON.
 * @param path Optional path for the endpoint. Method name is used by default.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Endpoint(val name: String = "")
