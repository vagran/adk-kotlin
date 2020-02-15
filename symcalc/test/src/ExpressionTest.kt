/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import io.github.vagran.adk.symcalc.Sin
import io.github.vagran.adk.symcalc.Variable
import io.github.vagran.adk.symcalc.Expression as E
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class BasicTest {

    val x = Variable("x")
    val y = Variable("y")
    val z = Variable("z")

    @Test
    fun Basic()
    {
        val e = E(-2.0) * x - Sin(y) + E(4.0)  * (-z) / (x pow 2.0)

        assertEquals("(-2.0) * x + sin(y) * (-1.0) + 4.0 * (-1.0) * z * (x^2.0)^(-1.0)", e.toString())
    }

    @Test
    fun OptimizeSum()
    {
        val e = E(3.0) * (y pow 0.0) + E(1.0) + E(2.0) * x - (x pow 1.0) + (x pow 2.0) + (x pow 2.0) * 2.0
        e.Optimize()
    }
}
