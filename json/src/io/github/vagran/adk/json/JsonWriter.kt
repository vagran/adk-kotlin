/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json

interface JsonWriter {

    fun WriteName(name: String)
    fun BeginObject()
    fun EndObject()
    fun BeginArray()
    fun EndArray()
    fun WriteNull()
    fun Write(value: Int)
    fun Write(value: Long)
    fun Write(value: Double)
    fun Write(value: String)
    fun Write(value: Boolean)

    fun Finish()
}
