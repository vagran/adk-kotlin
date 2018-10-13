package com.ast.adk.domain.httpserver

import com.ast.adk.GetStackTrace
import com.ast.adk.async.Deferred
import com.ast.adk.json.Json
import com.ast.adk.log.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException

/** Returned value is serialized into HTTP response body. Null can be returned instantly for null
 * result.
 */
private typealias HttpRequestHandler<T> = (request: HttpExchange) -> Deferred<T>?
private typealias HttpRequestHandlerAsync<T> = suspend (request: HttpExchange) -> T
/** May throw exception (presumably HttpError) to prevent from further processing. */
private typealias HttpRequestHook = suspend (request: HttpExchange) -> Unit

fun <T> HttpRequestHandlerAsync<T>.ToDeferredHandler(): HttpRequestHandler<T>
{
    return { Deferred.ForFunc { this.invoke(it) } }
}

open class HttpError(val code: Int,
                     message: String,
                     req: HttpExchange? = null):
    Exception(GetMessage(code, message, req)) {

    companion object {
        private fun GetMessage(code: Int,
                               message: String,
                               req: HttpExchange?): String
        {
            val buf = StringBuilder()
            if (req != null) {
                val remoteAddr = req.requestHeaders["X-remote-addr"]
                if (remoteAddr != null) {
                    buf.append('[')
                    buf.append(remoteAddr[0])
                    buf.append("] ")
                }
            }
            buf.append(code)
            buf.append(' ')
            buf.append(message)
            return buf.toString()
        }
    }
}

class HttpAuthError(val realm: String, req: HttpExchange? = null):
    HttpError(401, "Authorization required", req)

interface HttpRequestContext {
    val request: HttpExchange
}

class HttpDomainServer(private val httpServer: HttpServer,
                       val domainPrefix: String,
                       json: Json? = null) {

    var requestValidationHook: HttpRequestHook? = null
    var log: Logger? = null

    fun CreateHandler(handler: HttpRequestHandler<*>): HttpHandler
    {
        return HttpHandler {
            request: HttpExchange ->
            val method = request.requestMethod
            val uri = request.requestURI
            log?.Info("Request from %s - %s %s", request.remoteAddress, method, uri)
            try {
                Deferred.ForFunc {
                    requestValidationHook?.invoke(request)
                    val def = handler(request)
                    return@ForFunc def?.Await()
                }
                    .Subscribe { result, error -> OnRequestHandled(request, result, error) }
            } catch (e: Throwable) {
                OnRequestHandled(request, null, e)
            }
        }
    }

    fun Start()
    {
        httpServer.createContext(domainPrefix, CreateHandler(this::HandleRequest))
    }

    fun Stop()
    {
        httpServer.removeContext(domainPrefix)
    }

    fun CreateHandler(handler: HttpRequestHandlerAsync<*>): HttpHandler
    {
        return CreateHandler(handler.ToDeferredHandler())
    }

    fun MountController(prefix: String, controller: Any)
    {
        val path = domainPrefixPath.Append(HttpPath(prefix))
        val ctrlNode = CreateNode(path) { Node(controller) }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val json: Json = json ?: Json()
    private val nodes = HashMap<NodeKey, Node>()
    private val domainPrefixPath = HttpPath(domainPrefix)

    private inner class RequestContext (override val request: HttpExchange):
        HttpRequestContext {

    }

    private class Node(val controller: Any? = null) {

    }

    private class NodeKey(val name: String, val parent: Node?) {

        override fun equals(other: Any?): Boolean
        {
            val _other = other as NodeKey
            return parent === _other.parent && name == _other.name
        }

        override fun hashCode(): Int
        {
            return super.hashCode() xor name.hashCode()
        }
    }


    private fun OnRequestHandled(request: HttpExchange, result: Any?, error: Throwable?)
    {
        var _result = result
        val headers = request.responseHeaders
        headers.set("Content-Type", "application/json; charset=UTF-8")
        val code: Int
        if (error != null) {
            val msg = error.message
            _result = mapOf("fullText" to error.GetStackTrace(),
                            "message" to (msg ?: error::class.simpleName))
            if (error is HttpError) {
                code = error.code
                if (error is HttpAuthError) {
                    headers.set("WWW-Authenticate", "Basic realm=\"${error.realm}\"")
                }
            } else {
                code = 500
            }
            log?.Error(error,
                      "Request processing error ${request.requestMethod} ${request.requestURI}")
        } else {
            code = 200
        }
        try {
            if (_result is Unit) {
                request.sendResponseHeaders(code, -1)
            } else {
                request.sendResponseHeaders(code, 0)
                request.responseBody.use { json.ToJson(_result, it) }
            }
        } catch (e: IOException) {
            log?.Error(e, "Response writing failed")
        }

        request.close()
    }

    private fun HandleRequest(request: HttpExchange): Deferred<*>
    {
        //XXX
        return Deferred.Unit()
    }

    private fun CreateNode(path: HttpPath, fabric: () -> Node): Node
    {
        var parentNode: Node? = null
        for (i in 0 until path.length) {
            val comp = path.components[i]
            val nodeKey = NodeKey(comp, parentNode)
            if (i < path.length - 1) {
                parentNode = nodes.computeIfAbsent(nodeKey) { Node() }
                if (parentNode.controller != null) {
                    throw Error("Path intersects with mounted controlled: $path")
                }
            } else {
                if (nodes.containsKey(nodeKey)) {
                    throw Error("Node already exists: $path")
                }
                return fabric().also { nodes[nodeKey] = it }
            }
        }
        throw Error("Not reached")
    }
}
