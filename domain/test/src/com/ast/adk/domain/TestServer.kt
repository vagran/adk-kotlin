package com.ast.adk.domain

import com.ast.adk.async.Deferred
import com.ast.adk.async.ThreadContext
import com.ast.adk.domain.httpserver.HttpDomainServer
import com.ast.adk.domain.httpserver.HttpError
import com.ast.adk.domain.httpserver.HttpRequestContext
import com.ast.adk.json.Json
import com.ast.adk.json.TypeToken
import com.ast.adk.log.LogConfiguration
import com.ast.adk.log.LogManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.reflect.KClass


class WebServer {

    fun Start()
    {
        ctx.Start()

        httpServer.createContext("/test", domainServer.CreateHandler(this::Test))
        httpServer.createContext("/", domainServer.CreateHandler(this::DefaultHandler))
        domainServer.Start()

        httpServer.executor = ctx.GetExecutor()
        httpServer.start()

        domainServer.MountController("/Test", TestController())
    }

    fun Stop()
    {
        httpServer.stop(1)
        domainServer.Stop()
        ctx.Stop()
        logManager.Shutdown()
    }

    private fun DefaultHandler(request: HttpExchange): Nothing
    {
        throw HttpError(404, "Endpoint not found", request)
    }

    suspend fun Test(request: HttpExchange): String
    {
        return "TestResponse"
    }

    private val logManager = LogManager()
    init {
        logManager.Initialize(LogConfiguration.Default(
            LogConfiguration.Appender.ConsoleParams.Target.STDOUT))
    }

    private val log = logManager.GetLogger("WebServer")

    private val ctx: ThreadContext = ThreadContext("WebServer") {
        msg, error ->
        log.Error(error, msg)
    }

    private val httpServer = HttpServer.create(InetSocketAddress("0.0.0.0", 8086), 5)

    private val domainServer = HttpDomainServer(httpServer, "/domain")
    init {
        domainServer.log = log
    }
}

class TestController {

    class Request {
        lateinit var s: String
        var i = 0
    }

    class Result {
        lateinit var s: String
        var i = 0
    }

    @Endpoint
    fun Test1(): String
    {
        return "test1"
    }

    @Endpoint(name = "test_2")
    fun Test2(): Deferred<String>
    {
        return Deferred.ForResult("test2")
    }

    @Endpoint
    suspend fun Test3(): String
    {
        return "test3"
    }

    @Endpoint
    suspend fun Test4(ctx: HttpRequestContext): String
    {
        return "test4 " + ctx.request.requestURI
    }

    @Endpoint
    fun Test5()
    {
        println("test5")
    }

    @Endpoint
    suspend fun Test6(): Result
    {
        return Result().also {
            it.s = "test6"
            it.i = 42
        }
    }

    @Endpoint
    suspend fun Test7(req: Request): Result
    {
        return Result().also {
            it.s = "test7 " + req.s
            it.i = 42 + req.i
        }
    }

    @Endpoint(isRepository = true)
    suspend fun Entity(id: String): TestEntity
    {
        val _id = id.toInt()
        if (_id > 42) {
            throw HttpError(404, "Entity not found")
        }
        return TestEntity(_id)
    }
}

class TestEntity(val id: Int) {

    @Endpoint
    fun Delete()
    {
        println("deleting #$id")
    }

    @Endpoint
    fun SomeMethod(): String
    {
        return "TestEntity #$id"
    }
}

//fun main(args: Array<String>)
//{
//    WebServer().also {
//        it.Start()
//        Runtime.getRuntime().addShutdownHook(Thread(it::Stop))
//    }
//    while (true) {
//        Thread.sleep(1000)
//    }
//}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ServerTest {

    private class ResponseError(val code: Int): Exception()

    private val webServer = WebServer()
    private val httpClient = HttpClient.newBuilder().build()
    private val json = Json(true)

    @BeforeAll
    fun Setup()
    {
        webServer.Start()
    }

    @AfterAll
    fun Teardown()
    {
        webServer.Stop()
    }

    private inline fun <reified T: Any> SendRequest(url: String, body: Any?): T?
    {
        return SendRequest(url, body, T::class)
    }

    private fun <T: Any> SendRequest(url: String, body: Any?, responseCls: KClass<T>): T?
    {
        val reqBuilder = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:8086$url"))
        if (body == null) {
            reqBuilder.GET()
        } else {
            reqBuilder.POST(HttpRequest.BodyPublishers.ofString(json.ToJson(body)))
        }
        val req = reqBuilder.build()
        val response = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream())
        val code = response.statusCode()
        if (code < 200 || code >= 300) {
            throw ResponseError(code)
        }
        return if (responseCls == Unit::class) {
            val stream = response.body()
            if (stream.read() != -1) {
                throw Error("Unexpected response body")
            }
            null
        } else {
            json.FromJson(response.body(), TypeToken.Create(responseCls).type)
        }
    }

    fun CheckError(code: Int, func: () -> Unit)
    {
        var cought = false
        try {
            func()
        } catch (e: ResponseError) {
            assertEquals(code, e.code)
            cought = true
        }
        assertTrue(cought)
    }

    @Test
    fun RawEndpoint()
    {
        assertEquals("TestResponse", SendRequest<String>("/test", null))
    }

    @Test
    fun NonExistent()
    {
        CheckError(404) { SendRequest<Any>("/NonExistent", null) }
    }

    @Test
    fun DomainNonExistent()
    {
        CheckError(404) { SendRequest<Any>("/domain/NonExistent", null) }
    }

    @Test
    fun DomainPartial()
    {
        CheckError(404) { SendRequest<Any>("/domain", null) }
    }

    @Test
    fun DomainTest1()
    {
        assertEquals("test1", SendRequest<String>("/domain/Test/Test1", null))
    }

    @Test
    fun DomainTest2()
    {
        assertEquals("test2", SendRequest<String>("/domain/Test/test_2", null))
    }

    @Test
    fun DomainTest3()
    {
        assertEquals("test3", SendRequest<String>("/domain/Test/Test3", null))
    }

    @Test
    fun DomainTest4()
    {
        assertEquals("test4 /domain/Test/Test4", SendRequest<String>("/domain/Test/Test4", null))
    }

    @Test
    fun DomainTest5()
    {
        SendRequest<Unit>("/domain/Test/Test5", null)
    }

    @Test
    fun DomainTest6()
    {
        val result = SendRequest<TestController.Result>("/domain/Test/Test6", null) ?: throw Error()
        assertEquals("test6", result.s)
        assertEquals(42, result.i)
    }

    @Test
    fun DomainTest7()
    {
        val req = TestController.Request().also {
            it.s = "abc"
            it.i = 12
        }
        val result = SendRequest<TestController.Result>("/domain/Test/Test7", req) ?: throw Error()
        assertEquals("test7 abc", result.s)
        assertEquals(54, result.i)
    }

    @Test
    fun UnexpectedPost()
    {
        CheckError(400) { SendRequest<String>("/domain/Test/Test3", "abc") }
    }
}
