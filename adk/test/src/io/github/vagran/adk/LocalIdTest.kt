/*
 * This file is part of CCTrader project.
 * Copyright (c) 2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalIdTest {

    @Test
    fun TimestampTest()
    {
        val id = LocalId()
        val zoneRules = ZoneId.systemDefault().rules
        val now = LocalDateTime.now()
        val nowTs = now.toEpochSecond(zoneRules.getOffset(now))
        assertTrue(abs(nowTs - id.timestamp) < 3)
    }
}