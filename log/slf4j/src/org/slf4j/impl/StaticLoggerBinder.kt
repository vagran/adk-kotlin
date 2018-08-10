package org.slf4j.impl

import com.ast.adk.log.slf4j.Slf4jLoggerFactory
import org.slf4j.ILoggerFactory
import org.slf4j.spi.LoggerFactoryBinder

class StaticLoggerBinder private constructor(): LoggerFactoryBinder {
    companion object {
        @JvmStatic
        val singleton = StaticLoggerBinder()
    }

    override fun getLoggerFactory(): ILoggerFactory
    {
        return logFactory
    }

    override fun getLoggerFactoryClassStr(): String
    {
        return Slf4jLoggerFactory::class.java.name
    }

    private val logFactory = Slf4jLoggerFactory()
}
