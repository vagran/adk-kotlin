/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.async.db.mongo

import org.bson.codecs.Codec

/** Custom codecs may require two-step initialization for proper dependencies resolving. They should
 * implement this interface in such case.
 */
interface MongoCodec<T>: Codec<T> {
    fun Initialize(mapper: MongoMapper)
}
