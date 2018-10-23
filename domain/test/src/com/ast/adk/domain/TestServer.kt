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
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
        httpServer.stop(0)
        domainServer.Stop()
        ctx.Stop()
        logManager.Shutdown()
    }

    private fun DefaultHandler(ctx: HttpRequestContext): Nothing
    {
        throw HttpError(404, "Endpoint not found", ctx)
    }

    suspend fun Test(ctx: HttpRequestContext): String
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

    @Endpoint
    fun Null1(): String?
    {
        return null
    }

    @Endpoint
    fun Null2(): Deferred<String>?
    {
        return null
    }

    @Endpoint
    fun Null3(): Deferred<String?>?
    {
        return Deferred.ForResult(null)
    }

    @Endpoint
    suspend fun Null4(): String?
    {
        return null
    }

    @Endpoint(isRepository = true)
    fun Entity(id: String): Deferred<TestEntity>
    {
        val _id = id.toInt()
        if (_id > 42) {
            throw HttpError(404, "Entity not found")
        }
        return Deferred.ForResult(TestEntity().also { it.id = _id })
    }

    @Endpoint(isRepository = true)
    fun EntityNoIdArg(ctx: HttpRequestContext): TestEntity
    {
        val e = TestEntity()
        e.id = 42
        e.s = ctx.request.requestURI.path
        return e
    }

    @RepositoryIdConverter(entityClass = RecursiveEntity::class)
    fun RecursiveEntityIdConverter(s: String): Long
    {
        return s.toLong()
    }

    @Endpoint(isRepository = true)
    fun RecursiveEntity(id: Long): RecursiveEntity
    {
        if (id > 42) {
            throw HttpError(404, "Entity not found")
        }
        return RecursiveEntity().also {
            it.id = id
            it.sum = id
        }
    }
}


class TestEntity {
    var id: Int = 0
    var s: String = ""

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

    @Endpoint
    suspend fun MethodWithArg(x: Int): Int
    {
        return x + id + 10
    }
}


class RecursiveEntity {
    var id: Long = 0
    var sum: Long = 0

    @Endpoint
    fun SomeMethod(): String
    {
        return "RecursiveEntity #$id"
    }

    @RepositoryIdConverter(entityClass = RecursiveEntity::class)
    fun IdConverter(s: String): Long
    {
        return s.toLong()
    }

    @Endpoint(isRepository = true)
    fun Child(id: Long): RecursiveEntity
    {
        if (id > 33) {
            throw HttpError(404, "Entity not found")
        }
        return RecursiveEntity().also {
            it.id = id
            it.sum = sum + id
        }
    }
}


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
            response.body().copyTo(System.out)
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
    fun DomainEndpointSubPath()
    {
        CheckError(400) { SendRequest<String>("/domain/Test/Test1/sub/path", null) }
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

    @Test
    fun NullTest1()
    {
        assertNull(SendRequest<String>("/domain/Test/Null1", null))
    }

    @Test
    fun NullTest2()
    {
        assertNull(SendRequest<String>("/domain/Test/Null2", null))
    }

    @Test
    fun NullTest3()
    {
        assertNull(SendRequest<String>("/domain/Test/Null3", null))
    }

    @Test
    fun NullTest4()
    {
        assertNull(SendRequest<String>("/domain/Test/Null4", null))
    }

    @Test
    fun EntityMissingId()
    {
        CheckError(400) { SendRequest<TestEntity>("/domain/Test/Entity", null) }
    }

    @Test
    fun EntityTest1()
    {
        val e = SendRequest<TestEntity>("/domain/Test/Entity/12", null)!!
        assertEquals(12, e.id)
    }

    @Test
    fun EntityTest2()
    {
        CheckError(404) { SendRequest<TestEntity>("/domain/Test/Entity/43", null) }
    }

    @Test
    fun EntityTest3()
    {
        assertEquals("TestEntity #12", SendRequest<String>("/domain/Test/Entity/12/SomeMethod", null))
    }

    @Test
    fun EntityTest4()
    {
        CheckError(404) { SendRequest<TestEntity>("/domain/Test/Entity/43/SomeMethod", null) }
    }

    @Test
    fun EntityTest5()
    {
        SendRequest<Unit>("/domain/Test/Entity/12/Delete", null)
    }

    @Test
    fun EntityTest6()
    {
        assertEquals(42, SendRequest<Int>("/domain/Test/Entity/12/MethodWithArg", 20))
    }

    @Test
    fun EntityNoIdArgTest1()
    {
        val e = SendRequest<TestEntity>("/domain/Test/EntityNoIdArg", null)!!
        assertEquals(42, e.id)
        assertEquals("/domain/Test/EntityNoIdArg", e.s)
    }

    @Test
    fun EntityNoIdArgTest2()
    {
        CheckError(404) { SendRequest<TestEntity>("/domain/Test/EntityNoIdArg/12", null) }
    }

    @Test
    fun EntityNoIdArgTest3()
    {
        assertEquals("TestEntity #42", SendRequest<String>("/domain/Test/EntityNoIdArg/SomeMethod", null))
    }

    @Test
    fun RecursiveEntityTest1()
    {
        val e = SendRequest<RecursiveEntity>("/domain/Test/RecursiveEntity/5", null)!!
        assertEquals(5, e.id)
    }

    @Test
    fun RecursiveEntityTest2()
    {
        val s = SendRequest<String>("/domain/Test/RecursiveEntity/5/SomeMethod", null)
        assertEquals("RecursiveEntity #5", s)
    }

    @Test
    fun RecursiveEntityTest3()
    {
        val e = SendRequest<RecursiveEntity>("/domain/Test/RecursiveEntity/5/Child/7", null)!!
        assertEquals(7, e.id)
        assertEquals(12, e.sum)
    }

    @Test
    fun RecursiveEntityTest4()
    {
        val e = SendRequest<RecursiveEntity>("/domain/Test/RecursiveEntity/5/Child/7/Child/17", null)!!
        assertEquals(17, e.id)
        assertEquals(29, e.sum)
    }

    @Test
    fun RecursiveEntityTest5()
    {
        CheckError(404) {
            SendRequest<RecursiveEntity>("/domain/Test/RecursiveEntity/5/Child/7/Child/45", null)!!
        }
    }
}
