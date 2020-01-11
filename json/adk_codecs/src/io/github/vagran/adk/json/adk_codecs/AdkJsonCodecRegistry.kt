/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.json.adk_codecs

import io.github.vagran.adk.LocalId
import io.github.vagran.adk.json.JsonCodecRegistry
import io.github.vagran.adk.json.adk_codecs.codecs.LocalIdJsonCodec

object adkJsonCodecRegistry: JsonCodecRegistry() {
    init {
        classCodecs[LocalId::class] = { LocalIdJsonCodec() }
    }
}
