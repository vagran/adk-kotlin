package io.github.vagran.adk.json

import java.lang.Exception

class JsonReadError(msg: String, cause: Throwable? = null):
    Exception(msg, cause)
