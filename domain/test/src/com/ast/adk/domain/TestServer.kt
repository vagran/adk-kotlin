package com.ast.adk.domain

import com.ast.adk.async.Deferred
import com.ast.adk.async.ThreadContext
import com.ast.adk.domain.httpserver.HttpDomainServer
import com.ast.adk.domain.httpserver.HttpError
import com.ast.adk.log.LogConfiguration
import com.ast.adk.log.LogManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress


class WebServer {

    fun Start()
    {
        ctx.Start()

        httpServer.createContext("/test", domainServer.CreateHandler(this::Test))
        httpServer.createContext("/", domainServer.CreateHandler(this::DefaultHandler))

        httpServer.executor = ctx.GetExecutor()
        httpServer.start()

        domainServer.CreateController("/Test", TestController())
    }

    fun Stop()
    {
        httpServer.stop(1)
        ctx.Stop()
        logManager.Shutdown()
    }

    private fun DefaultHandler(request: HttpExchange): Nothing
    {
        throw HttpError(404, "Endpoint not found", request)
    }

    suspend fun Test(request: HttpExchange): String
    {
        return "aaaa"
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

    private val domainServer = HttpDomainServer(httpServer)
    init {
        domainServer.log = log
    }
}

class TestController {

    @Endpoint
    fun Test1(): String
    {
        return "test1"
    }

    @Endpoint
    fun Test2(): Deferred<String>
    {
        return Deferred.ForResult("test2")
    }

    @Endpoint
    suspend fun Test3(): String
    {
        return "test3"
    }
}

class App {
    private val webServer = WebServer()

    fun Run()
    {
        Runtime.getRuntime().addShutdownHook(Thread(this::Shutdown))
        webServer.Start()
        while (true) {
            Thread.sleep(1000)
        }
    }

    private fun Shutdown()
    {
        webServer.Stop()
    }
}

fun main(args: Array<String>)
{
    App().Run()
}
