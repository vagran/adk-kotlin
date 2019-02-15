package com.ast.adk.log

import java.io.PrintStream
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import kotlin.collections.ArrayList


class LogManager {

    fun Initialize(config: LogConfiguration)
    {
        this.config = config
        queue = LogQueue(config.settings.queueSize,
                         config.settings.overflowBlocks,
                         config.settings.queueCheckInterval)

        for (appenderConfig in config.appenders) {
            appenders[appenderConfig.name] = CreateAppender(appenderConfig)
        }

        appenderThread.start()

        isInitialized = true
    }

    fun Shutdown()
    {
        queue.Stop()
        appenderThread.join()
        for (appender in appenders.values) {
            appender.Close()
        }
    }

    fun GetLogger(name: String): Logger
    {
        return synchronized(loggers) {
            val logger = loggers[name]?.get()
            if (logger != null) {
                return@synchronized logger
            }
            val newLogger = CreateLogger(name)
            loggers[name] = WeakReference(newLogger, loggersRefQueue)
            return newLogger
        }
    }

    fun RedirectStderr(loggerName: String = "STDERR", level: LogLevel = LogLevel.ERROR)
    {
        System.setErr(PrintStream(LoggingOutputStream(GetLogger(loggerName), level), true))
    }

    /** Redirect messages logged via java.util.logging facilities.
     * @param loggerName Logger name for all messages from built-in logging.
     */
    fun RedirectBuiltinLog(loggerName: String)
    {
        val logger = GetLogger(loggerName)
        val builtinLogManager = java.util.logging.LogManager.getLogManager()
        builtinLogManager.reset()
        val rootLogger = builtinLogManager.getLogger("")

        rootLogger.addHandler(object: Handler() {
            val formatter = java.util.logging.SimpleFormatter()

            override fun publish(record: LogRecord)
            {
                val intLevel = record.level.intValue()
                val level = when {
                    intLevel >= Level.SEVERE.intValue() -> LogLevel.ERROR
                    intLevel >= Level.WARNING.intValue() -> LogLevel.WARNING
                    intLevel >= Level.INFO.intValue() -> LogLevel.INFO
                    intLevel >= Level.CONFIG.intValue() -> LogLevel.DEBUG
                    else -> LogLevel.TRACE
                }
                logger.Log(level, record.thrown) {
                    "${record.loggerName} - ${formatter.formatMessage(record)}"
                }
            }

            override fun flush() {}
            override fun close() {}
        })
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private var isInitialized = false
    private lateinit var config: LogConfiguration
    private lateinit var queue: LogQueue<LogMessage>
    private val appenders = TreeMap<String, Appender>()
    private val appenderThread = Thread(this::AppenderThreadFunc, "AdkLogAppender")
    private val loggers = HashMap<String, WeakReference<Logger>>()
    private var lastCleanup: Long = System.currentTimeMillis()
    private val loggersRefQueue = ReferenceQueue<Logger>()

    companion object {
        private const val LOGGERS_CLEANUP_PERIOD = 20_000L
    }


    inner class LoggerImpl(name: String,
                           thresholdLevel: LogLevel,
                           private val appenders: List<Appender>):
        Logger(name, thresholdLevel) {

        override fun WriteLog(level: LogLevel, exception: Throwable?, msgText: String)
        {
            val msg = LogMessage()
            msg.SetTimestampNow()
            msg.level = level
            msg.msg = msgText
            msg.exception = exception
            msg.loggerName = name
            msg.appenders = appenders
            if (envMask.IsSet(EnvMask.Resource.THREAD_NAME)) {
                msg.threadName = Thread.currentThread().name
            }
            queue.Push(msg)
        }

        private val envMask = EnvMask()

        init {
            for (appender in appenders) {
                envMask.Merge(appender.envMask)
            }
        }
    }

    private fun CreateAppender(appenderConfig: LogConfiguration.Appender): Appender
    {
        return when (appenderConfig.type) {
            LogConfiguration.Appender.Type.CONSOLE -> ConsoleAppender(appenderConfig)
            LogConfiguration.Appender.Type.FILE -> FileAppender(appenderConfig)
        }
    }

    private fun AppenderThreadFunc()
    {
        while (true) {
            val msg = queue.Pop {
                val now = System.currentTimeMillis()
                if (now - lastCleanup >= LOGGERS_CLEANUP_PERIOD) {
                    lastCleanup = now
                    CleanupLoggers()
                }
            } ?: break
            for (appender in msg.appenders) {
                appender.AppendMessage(msg)
            }
        }
    }

    private fun CreateLogger(name: String): Logger
    {
        if (!isInitialized) {
            throw Error("Attempting to get logger before initialized: $name")
        }
        val loggerParams = config.ConfigureLogger(name)

        var level: LogLevel? = null
        for (appender in loggerParams.appenders) {
            if (level == null || (appender.level != null && appender.level!! < level)) {
                level = appender.level
            }
        }
        if (level == null || loggerParams.level > level) {
            level = loggerParams.level
        }

        val appenders = ArrayList<Appender>()
        for (appenderConfig in loggerParams.appenders) {
            appenders.add(this.appenders[appenderConfig.name]!!)
        }

        return LoggerImpl(name, level, appenders)
    }

    private fun CleanupLoggers()
    {
        var refSeen = false
        while (loggersRefQueue.poll() != null) {
            refSeen = true
        }
        if (!refSeen) {
            return
        }
        synchronized(loggers) {
            val it = loggers.iterator()
            while (it.hasNext()) {
                if (it.next().value.get() == null) {
                    it.remove()
                }
            }
        }
    }
}
