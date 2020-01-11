package io.github.vagran.adk.log.slf4j

import io.github.vagran.adk.log.LogLevel
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.helpers.MessageFormatter

class Slf4jLoggerAdapter(private val logger: io.github.vagran.adk.log.Logger): Logger {
    override fun warn(msg: String)
    {
        logger.Warning(msg)
    }

    override fun warn(format: String?, arg: Any?)
    {
        if (isWarnEnabled) {
            logger.Warning(MessageFormatter.format(format, arg).message)
        }
    }

    override fun warn(format: String?, vararg arguments: Any?)
    {
        if (isWarnEnabled) {
            logger.Warning(MessageFormatter.arrayFormat(format, arguments).message)
        }
    }

    override fun warn(format: String?, arg1: Any?, arg2: Any?)
    {
        if (isWarnEnabled) {
            logger.Warning(MessageFormatter.format(format, arg1, arg2).message)
        }
    }

    override fun warn(msg: String, t: Throwable?)
    {
        logger.Warning(t, msg)
    }

    override fun warn(marker: Marker?, msg: String)
    {
        warn(msg)
    }

    override fun warn(marker: Marker?, format: String?, arg: Any?)
    {
        warn(format, arg)
    }

    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?)
    {
        warn(format, arg1, arg2)
    }

    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?)
    {
        warn(format, arguments)
    }

    override fun warn(marker: Marker?, msg: String, t: Throwable?)
    {
        logger.Warning(t, msg)
    }

    override fun getName(): String
    {
        return logger.name
    }

    override fun info(msg: String)
    {
        logger.Info(msg)
    }

    override fun info(format: String?, arg: Any?)
    {
        if (isInfoEnabled) {
            logger.Info(MessageFormatter.format(format, arg).message)
        }
    }

    override fun info(format: String?, arg1: Any?, arg2: Any?)
    {
        if (isInfoEnabled) {
            logger.Info(MessageFormatter.format(format, arg1, arg2).message)
        }
    }

    override fun info(format: String?, vararg arguments: Any?)
    {
        if (isInfoEnabled) {
            logger.Info(MessageFormatter.arrayFormat(format, arguments).message)
        }
    }

    override fun info(msg: String, t: Throwable?)
    {
        logger.Info(t, msg)
    }

    override fun info(marker: Marker?, msg: String) {
        info(msg)
    }

    override fun info(marker: Marker?, format: String?, arg: Any?)
    {
        info(format, arg)
    }

    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?)
    {
        info(format, arg1, arg2)
    }

    override fun info(marker: Marker?, format: String?, vararg arguments: Any?)
    {
        info(format, arguments)
    }

    override fun info(marker: Marker?, msg: String, t: Throwable?)
    {
        logger.Info(t, msg)
    }

    override fun isErrorEnabled(): Boolean
    {
        return logger.thresholdLevel <= LogLevel.ERROR
    }

    override fun isErrorEnabled(marker: Marker?): Boolean {
        return logger.thresholdLevel <= LogLevel.ERROR
    }

    override fun error(msg: String)
    {
        logger.Error(msg)
    }

    override fun error(format: String?, arg: Any?)
    {
        if (isErrorEnabled) {
            logger.Error(MessageFormatter.format(format, arg).message)
        }
    }

    override fun error(format: String?, arg1: Any?, arg2: Any?)
    {
        if (isErrorEnabled) {
            logger.Error(MessageFormatter.format(format, arg1, arg2).message)
        }
    }

    override fun error(format: String?, vararg arguments: Any?)
    {
        if (isErrorEnabled) {
            logger.Error(MessageFormatter.arrayFormat(format, arguments).message)
        }
    }

    override fun error(msg: String, t: Throwable?)
    {
        logger.Error(t, msg)
    }

    override fun error(marker: Marker?, msg: String)
    {
        error(msg)
    }

    override fun error(marker: Marker?, format: String?, arg: Any?)
    {
        error(format, arg)
    }

    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?)
    {
        error(format, arg1, arg2)
    }

    override fun error(marker: Marker?, format: String?, vararg arguments: Any?)
    {
        error(format, arguments)
    }

    override fun error(marker: Marker?, msg: String, t: Throwable?)
    {
        logger.Error(t, msg)
    }

    override fun isDebugEnabled(): Boolean
    {
        return logger.thresholdLevel <= LogLevel.DEBUG
    }

    override fun isDebugEnabled(marker: Marker?): Boolean
    {
        return logger.thresholdLevel <= LogLevel.DEBUG
    }

    override fun debug(msg: String)
    {
        logger.Debug(msg)
    }

    override fun debug(format: String?, arg: Any?)
    {
        if (isDebugEnabled) {
            logger.Debug(MessageFormatter.format(format, arg).message)
        }
    }

    override fun debug(format: String?, arg1: Any?, arg2: Any?)
    {
        if (isDebugEnabled) {
            logger.Debug(MessageFormatter.format(format, arg1, arg2).message)
        }
    }

    override fun debug(format: String?, vararg arguments: Any?)
    {
        if (isDebugEnabled) {
            logger.Debug(MessageFormatter.arrayFormat(format, arguments).message)
        }
    }

    override fun debug(msg: String, t: Throwable?)
    {
        logger.Debug(t, msg)
    }

    override fun debug(marker: Marker?, msg: String)
    {
        debug(msg)
    }

    override fun debug(marker: Marker?, format: String?, arg: Any?)
    {
        debug(format, arg)
    }

    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?)
    {
        debug(format, arg1, arg2)
    }

    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?)
    {
        debug(format, arguments)
    }

    override fun debug(marker: Marker?, msg: String, t: Throwable?)
    {
        logger.Debug(t, msg)
    }

    override fun isInfoEnabled(): Boolean
    {
        return logger.thresholdLevel <= LogLevel.INFO
    }

    override fun isInfoEnabled(marker: Marker?): Boolean
    {
        return logger.thresholdLevel <= LogLevel.INFO
    }

    override fun trace(msg: String)
    {
        logger.Trace(msg)
    }

    override fun trace(format: String?, arg: Any?)
    {
        if (isTraceEnabled) {
            logger.Trace(MessageFormatter.format(format, arg).message)
        }
    }

    override fun trace(format: String?, arg1: Any?, arg2: Any?)
    {
        if (isTraceEnabled) {
            logger.Trace(MessageFormatter.format(format, arg1, arg2).message)
        }
    }

    override fun trace(format: String?, vararg arguments: Any?)
    {
        if (isTraceEnabled) {
            logger.Trace(MessageFormatter.arrayFormat(format, arguments).message)
        }
    }

    override fun trace(msg: String, t: Throwable?)
    {
        logger.Trace(t, msg)
    }

    override fun trace(marker: Marker?, msg: String)
    {
        trace(msg)
    }

    override fun trace(marker: Marker?, format: String?, arg: Any?)
    {
        trace(format, arg)
    }

    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?)
    {
        trace(format, arg1, arg2)
    }

    override fun trace(marker: Marker?, format: String?, vararg arguments: Any?)
    {
        trace(format, arguments)
    }

    override fun trace(marker: Marker?, msg: String, t: Throwable?)
    {
        logger.Trace(t, msg)
    }

    override fun isWarnEnabled(): Boolean
    {
        return logger.thresholdLevel <= LogLevel.WARNING
    }

    override fun isWarnEnabled(marker: Marker?): Boolean
    {
        return logger.thresholdLevel <= LogLevel.WARNING
    }

    override fun isTraceEnabled(): Boolean
    {
        return logger.thresholdLevel <= LogLevel.TRACE
    }

    override fun isTraceEnabled(marker: Marker?): Boolean
    {
        return logger.thresholdLevel <= LogLevel.TRACE
    }

}
