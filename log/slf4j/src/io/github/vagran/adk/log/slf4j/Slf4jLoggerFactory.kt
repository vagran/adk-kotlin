package io.github.vagran.adk.log.slf4j

import io.github.vagran.adk.log.slf4j.api.Slf4jLogManager
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

class Slf4jLoggerFactory: ILoggerFactory {

    override fun getLogger(name: String): Logger
    {
        return Slf4jLoggerAdapter(Slf4jLogManager.logManager.GetLogger(name))
    }
}
