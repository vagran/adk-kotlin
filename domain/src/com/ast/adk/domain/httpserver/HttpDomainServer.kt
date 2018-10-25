package com.ast.adk.domain.httpserver

import com.ast.adk.GetStackTrace
import com.ast.adk.async.Deferred
import com.ast.adk.domain.Endpoint
import com.ast.adk.domain.RepositoryIdConverter
import com.ast.adk.json.Json
import com.ast.adk.json.JsonSerializer
import com.ast.adk.log.Logger
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.*
import kotlin.reflect.jvm.jvmErasure

/** Returned value is serialized into HTTP response body. Null can be returned instantly for null
 * result.
 */
typealias HttpRequestHandler<T> = (request: HttpRequestContext) -> Deferred<T>?
typealias HttpRequestHandlerAsync<T> = suspend (request: HttpRequestContext) -> T
/** May throw exception (presumably HttpError) to prevent from further processing. */
typealias HttpRequestHook = suspend (request: HttpRequestContext) -> Unit

/** Method node references to. */
private typealias NodeMethod = suspend (Array<Any?>) -> Any?

fun <T> HttpRequestHandlerAsync<T>.ToDeferredHandler(): HttpRequestHandler<T>
{
    return { Deferred.ForFunc { this.invoke(it) } }
}

open class HttpError(val code: Int,
                     message: String,
                     req: HttpRequestContext? = null):
    Exception(GetMessage(code, message, req)) {

    companion object {
        private fun GetMessage(code: Int,
                               message: String,
                               req: HttpRequestContext?): String
        {
            val buf = StringBuilder()
            if (req != null) {
                val remoteAddr = req.request.requestHeaders["X-remote-addr"]
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

class HttpAuthError(val realm: String, req: HttpRequestContext? = null):
    HttpError(401, "Authorization required", req)

interface HttpRequestContext {
    val request: HttpExchange
}

class HttpDomainServer(private val httpServer: HttpServer,
                       val domainPrefix: String,
                       json: Json? = null,
                       private val unitResultMode: UnitResultMode = UnitResultMode.NO_CONTENT) {

    var requestValidationHook: HttpRequestHook? = null
    var log: Logger? = null

    enum class UnitResultMode {
        /** Empty body with HTTP 204 "No Content" result code. */
        NO_CONTENT,
        /** Null literal with HTTP 200 "OK" result code. */
        NULL
    }

    fun CreateHandler(handler: HttpRequestHandler<*>): HttpHandler
    {
        return HttpHandler {
            request: HttpExchange ->
            val ctx = RequestContext(request)
            val method = request.requestMethod
            val uri = request.requestURI
            log?.Info("Request from %s - %s %s", request.remoteAddress, method, uri)
            try {
                Deferred.ForFunc {
                    requestValidationHook?.invoke(ctx)
                    val def = handler(ctx)
                    return@ForFunc def?.Await()
                }
                    .Subscribe { result, error -> OnRequestHandled(ctx, result, error) }
            } catch (e: Throwable) {
                OnRequestHandled(ctx, null, e)
            }
        }
    }

    fun CreateHandler(handler: HttpRequestHandlerAsync<*>): HttpHandler
    {
        return CreateHandler(handler.ToDeferredHandler())
    }

    fun Start()
    {
        httpServer.createContext(domainPrefix, CreateHandler(this::HandleRequest))
    }

    fun Stop()
    {
        httpServer.removeContext(domainPrefix)
    }

    fun MountController(prefix: String, controller: Any)
    {
        val path = domainPrefixPath.Append(HttpPath(prefix))
        val ctrlNode = CreateNode(path) { Node(controller = controller) }
        MountController(ctrlNode, controller::class, HashMap())
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val json: Json = json ?: Json()
    private val nodes = HashMap<NodeKey, Node>()
    private val domainPrefixPath = HttpPath(domainPrefix)

    private inner class RequestContext (override val request: HttpExchange):
        HttpRequestContext

    private inner class Node(val controller: Any? = null,
                             func: KFunction<*>? = null,
                             annotation: Endpoint? = null,
                             entityIdConverters: Map<KClass<*>, EntityIdConverter>? = null) {
        val method: NodeMethod?
        /** Index of the argument for HttpRequestContext. */
        var ctxArgIdx = -1
        /** Index of the argument for request data. */
        var dataArgIdx = -1
        /** Serializer for data argument. */
        val dataSerializer: JsonSerializer<*>?
        /** Entity class for repository endpoint node, null for other nodes. */
        val repoEntityClass: KClass<*>?
        /** Converter for entity ID for repository endpoint, null if no converter or non-repository
         * node.
         */
        val entityIdConverter: EntityIdConverter?

        init {
            if (func == null) {
                method = null
                dataSerializer = null
                repoEntityClass = null
                entityIdConverter = null

            } else {
                val isRepository = annotation?.isRepository ?: false
                val repoEntityClass: KClass<*>
                when {
                    func.isSuspend -> {
                        method = { args -> func.callSuspend(*args) }
                        repoEntityClass = func.returnType.jvmErasure
                    }
                    func.returnType.jvmErasure.isSubclassOf(Deferred::class) -> {
                        method = { args -> (func.call(*args) as Deferred<*>?)?.Await() }
                        repoEntityClass = func.returnType.arguments[0].type?.jvmErasure ?:
                            throw Error("Star projection not allowed for returned deferred $func")
                    }
                    else -> {
                        method = { args -> func.call(*args) }
                        repoEntityClass = func.returnType.jvmErasure
                    }
                }

                if (func.parameters.size > 3) {
                    throw Error("Endpoint $func has too many arguments")
                }

                if (isRepository) {
                    this.repoEntityClass = repoEntityClass
                } else {
                    this.repoEntityClass = null
                }

                var dataSerializer: JsonSerializer<*>? = null
                var entityIdConverter: EntityIdConverter? = null
                for (i in 1 until func.parameters.size) {
                    val param = func.parameters[i]
                    if (param.type.jvmErasure.isSubclassOf(HttpRequestContext::class)) {
                        if (ctxArgIdx >= 0) {
                            throw Error("Context argument specified more than once in $func")
                        }
                        ctxArgIdx = i
                        continue
                    }
                    if (dataArgIdx >= 0) {
                        throw Error("Data argument specified more than once in $func")
                    }

                    if (isRepository) {
                        entityIdConverter = entityIdConverters?.get(repoEntityClass)
                        val entityIdClass = param.type.jvmErasure
                        if (entityIdConverter == null) {
                            if (!entityIdClass.isSubclassOf(String::class)) {
                                throw Error(
                                    "Entity ID argument type should be string for repository " +
                                    "endpoint $func without entity ID converter defined")
                            }

                        } else if (!entityIdConverter.idType.isSubclassOf(entityIdClass)) {
                            throw Error(
                                "Entity ID argument for endpoint $func has incompatible type " +
                                "with entity ID converter method ${entityIdConverter.func}")
                        }
                    }

                    dataArgIdx = i
                    dataSerializer = json.GetSerializer<Any>(param.type)
                }
                this.dataSerializer = dataSerializer
                this.entityIdConverter = entityIdConverter
            }
        }

        @Suppress("UNCHECKED_CAST")
        suspend fun InvokeMethod(ctx: HttpRequestContext, controller: Any, entityId: String?,
                                 isTerminalNode: Boolean): Any?
        {
            if (method == null) {
                throw IllegalStateException("Non-executable node invoked")
            }
            var argSize = 1
            if (ctxArgIdx != -1) {
                argSize++
            }
            val data =
                if (dataArgIdx != -1) {
                    argSize++
                    if (entityId != null) {
                        entityIdConverter?.Convert(controller, entityId) ?: entityId
                    } else {
                        GetRequestBody(ctx.request, dataSerializer as JsonSerializer<Any>)
                    }
                } else {
                    if (isTerminalNode && ctx.request.requestMethod != "GET") {
                        throw HttpError(400, "GET method expected")
                    }
                    null
                }
            val args = Array(argSize) {
                argIdx ->
                when (argIdx) {
                    0 -> controller
                    ctxArgIdx -> ctx
                    else -> data
                }
            }
            try {
                return method.invoke(args)
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                if (cause != null) {
                    cause.addSuppressed(e)
                    throw cause
                } else {
                    throw e
                }
            }
        }
    }

    private class NodeKey(val name: String, val parent: Node?) {

        override fun equals(other: Any?): Boolean
        {
            val _other = other as NodeKey
            return parent === _other.parent && name == _other.name
        }

        override fun hashCode(): Int
        {
            return parent.hashCode() xor name.hashCode()
        }
    }

    private class EntityIdConverter(val func: KFunction<*>) {
        val idType get() = func.returnType.jvmErasure

        init {
            if (func.parameters.size != 2) {
                throw Error("Entity ID converter method should accept one parameter: $func")
            }
            val paramType = func.parameters[1].type.jvmErasure
            if (!paramType.isSuperclassOf(String::class)) {
                throw Error("Entity ID converter method should accept string argument: $func")
            }
        }

        fun Convert(receiver: Any, idStr: String): Any
        {
            return func.call(receiver, idStr)
                ?: throw Error("Entity ID converter returned null for $idStr")
        }
    }


    private fun OnRequestHandled(ctx: HttpRequestContext, result: Any?, error: Throwable?)
    {
        val request = ctx.request
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
                when (unitResultMode) {
                    UnitResultMode.NO_CONTENT -> {
                        request.sendResponseHeaders(204, -1)
                    }
                    UnitResultMode.NULL -> {
                        request.sendResponseHeaders(code, 0)
                        request.responseBody.write("null".toByteArray(StandardCharsets.UTF_8))
                    }
                }
            } else {
                request.sendResponseHeaders(code, 0)
                request.responseBody.use { json.ToJson(_result, it) }
            }
        } catch (e: IOException) {
            log?.Error(e, "Response writing failed")
        }

        request.close()
    }

    private suspend fun HandleRequest(ctx: HttpRequestContext): Any?
    {
        val request = ctx.request
        val path = HttpPath(request.requestURI.rawPath)
        var controller: Any? = null
        var curNode: Node? = null
        var repoNode: Node? = null

        for (compIdx in 0 until path.components.size) {
            val pathComp = path.components[compIdx]
            val node: Node?
            val isLastComp = compIdx == path.components.size - 1

            if (repoNode == null) {
                node = nodes[NodeKey(pathComp, curNode)]
                    ?: throw HttpError(404, "Path not found", ctx)
                if (node.controller != null) {
                    controller = node.controller
                }
                if (node.method != null) {
                    if (controller == null) {
                        throw HttpError(500, "Controller not specified", ctx)
                    }
                    if (node.repoEntityClass == null) {
                        if (compIdx < path.components.size - 1) {
                            throw HttpError(400, "Endpoint sub-path requested", ctx)
                        }
                        return node.InvokeMethod(ctx, controller, null, isLastComp)
                    }
                    repoNode = node
                }
            } else {
                node = null
            }

            if (repoNode != null && (repoNode.dataArgIdx == -1 || node == null)) {
                val entityId = if (repoNode.dataArgIdx == -1) {
                    null
                } else {
                    pathComp
                }
                if (controller == null) {
                    throw HttpError(500, "Controller not specified", ctx)
                }
                val entity = repoNode.InvokeMethod(ctx, controller, entityId, isLastComp)
                    ?: throw HttpError(500, "Null entity returned", ctx)
                repoNode = null
                if (isLastComp) {
                    return entity
                }
                controller = entity
            }

            if (node != null) {
                curNode = node
            }
        }

        if (repoNode != null) {
            throw HttpError(400, "Entity ID not specified", ctx)
        }
        throw HttpError(404, "No handler found", ctx)
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

    private fun MountController(ctrlNode: Node, ctrlClass: KClass<*>,
                                cache: HashMap<KFunction<*>, Node>)
    {
        val entityIdConverters = HashMap<KClass<*>, EntityIdConverter>()
        /* Find all entity ID converters first. */
        for (func in ctrlClass.declaredMemberFunctions) {
            val ann = func.findAnnotation<RepositoryIdConverter>() ?: continue
            val prevConverter = entityIdConverters[ann.entityClass]
            if (prevConverter != null) {
                throw Error("Entity ID converter re-defined in $func, previous definition " +
                            "in ${prevConverter.func}")
            }
            entityIdConverters[ann.entityClass] = EntityIdConverter(func)
        }

        for (func in ctrlClass.declaredMemberFunctions) {
            val ann = func.findAnnotation<Endpoint>() ?: continue
            val name =
                if (ann.name.isEmpty()) {
                    func.name
                } else {
                    ann.name
                }
            var isRecursive = false
            val node: Node = run {
                val existingNode = cache[func]
                if (existingNode != null) {
                    isRecursive = true
                    return@run existingNode
                }
                val node = Node(func = func, annotation = ann,
                                entityIdConverters = entityIdConverters)
                cache[func] = node
                return@run node
            }
            nodes[NodeKey(name, ctrlNode)] = node
            if (!isRecursive && node.repoEntityClass != null) {
                MountController(node, node.repoEntityClass, cache)
            }
        }
    }

    private fun <T: Any> GetRequestBody(request: HttpExchange, serializer: JsonSerializer<T>): T
    {
        if (request.requestMethod != "POST") {
            throw Error("POST method expected")
        }
        request.requestBody.use { body ->
            return serializer.FromJson(body) ?: throw IllegalArgumentException("Null not allowed")
        }
    }
}
