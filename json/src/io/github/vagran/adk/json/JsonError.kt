/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json

import java.lang.Exception

class JsonReadError(msg: String, cause: Throwable? = null):
    Exception(msg, cause)
