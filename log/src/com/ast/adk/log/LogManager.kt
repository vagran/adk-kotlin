package com.ast.adk.log

class LogManager {

    fun Initialize(config: Configuration)
    {
        this.config = config

    }

    fun Shutdown()
    {

    }

    fun GetLogger(name: String): Logger
    {
        TODO()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var config: Configuration
}
