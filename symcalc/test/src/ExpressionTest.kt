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
    fun OptimizeSumWithZero()
    {
        val e = E(0.0) + x + 0.0 + y + 0.0 + 1.0 + 0.0
        val eOpt = e.Optimize()
        assertEquals("x + y + 1.0", eOpt.toString())
        /* Check sum promotion as well */
        assert(x === eOpt.funcArgs!![0].variable)
        assert(y === eOpt.funcArgs!![1].variable)
        assertEquals(1.0, eOpt.funcArgs!![2].constant)
    }

    @Test
    fun OptimizeDegenerateSum()
    {
        val e = x + 0.0
        val eOpt = e.Optimize()
        assertEquals("x", eOpt.toString())
        assert(x === eOpt.variable)
    }

    @Test
    fun OptimizeDegenerateSum2()
    {
        val e = E(0.0) + 0.0
        val eOpt = e.Optimize()
        assertEquals("0.0", eOpt.toString())
        assertEquals(0.0, eOpt.constant)
    }

    @Test
    fun OptimizeSum()
    {
        val e = E(0.0) + E(3.0) * (y pow 0.0) + E(1.0) + E(0.0) + E(2.0) * x -
            (x pow 1.0) + (x pow 2.0) + (x pow 2.0) * 2.0 + E(0.0)
        println(e.Optimize().toString())
    }
}
