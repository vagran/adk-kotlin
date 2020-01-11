package io.github.vagran.adk.omm

import java.lang.Exception

class OmmError(msg: String, cause: Throwable? = null):
    Exception(msg, cause)
