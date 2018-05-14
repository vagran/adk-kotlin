package com.ast.adk

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.config.*
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.message.StringFormatterMessageFactory
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.util.function.Consumer


object Log {

    fun GetLogger(name: String): Logger
    {
        return LogManager.getLogger(name, defMsgFactory)
    }

    /** Redirect stderr to log.  */
    fun RedirectStderr()
    {
        System.setErr(PrintStream(
            LoggingOutputStream(GetLogger("STDERR"), Level.ERROR), true))
    }

    fun GetStackTrace(t: Throwable): String
    {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        t.printStackTrace(pw)
        return sw.toString()
    }

    /** Call this method to manually shutdown logging subsystem. It is useful when there is logging
     * in shutdown hook. The log4j shutdown hook should be disabled in the configuration by adding
     * shutdownHook="disable" attribute to "Configuration" tag.
     */
    fun Shutdown()
    {
        val logger = GetLogger("Log.Shutdown")
        if (LogManager.getContext() is LoggerContext) {
            logger.info("Shutting down log4j2")
            Configurator.shutdown(LogManager.getContext() as LoggerContext)
        } else {
            logger.warn("Unable to shutdown log4j2")
        }
    }

    /** Initialize logging for unit tests.  */
    fun InitTestLogging(vararg loggers: LoggerConfig)
    {
        ConfigurationFactory.setConfigurationFactory(
            TestLoggingConfiguration.ConfigFactory(loggers))
    }

    /** Configuration for logging in unit tests.  */
    internal object TestLoggingConfiguration {

        const val PATTERN_LAYOUT = "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"

        @Plugin(category = "ConfigurationFactory", name = "ConfigFactory")
        class ConfigFactory(private val loggers: Array<out LoggerConfig>): ConfigurationFactory()
        {
            override fun getSupportedTypes(): Array<String>?
            {
                return null
            }

            override fun getConfiguration(loggerContext: LoggerContext,
                                          source: ConfigurationSource):
                Configuration
            {
                return TestLoggingConfig(loggers)
            }

            override fun getConfiguration(loggerContext: LoggerContext, name: String?,
                                          configLocation: URI?): Configuration
            {
                return TestLoggingConfig(loggers)
            }
        }

        internal class TestLoggingConfig(loggers: Array<out LoggerConfig>) : DefaultConfiguration() {

            init {
                name = "Unit test log"
                val layout = PatternLayout.newBuilder()
                        .withPattern(PATTERN_LAYOUT).build()
                val builder = ConsoleAppender::class.java.getMethod("newBuilder")
                        .invoke(null) as ConsoleAppender.Builder<*>
                val appender = builder
                        .setTarget(ConsoleAppender.Target.SYSTEM_OUT)
                        .withLayout(layout)
                        .withName("ConsoleAppender").build()
                addAppender(appender)
                val root = rootLogger
                /* Remove all default appenders. */
                root.appenders.keys.forEach(Consumer<String> { root.removeAppender(it) })
                root.addAppender(appender, Level.DEBUG, null)
                root.level = Level.DEBUG

                for (config in loggers) {
                    addLogger(config.name, config)
                }
            }

        }

    }

    private val defMsgFactory = StringFormatterMessageFactory()
}