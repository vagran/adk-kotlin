/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import io.github.vagran.adk.symcalc.Exp
import io.github.vagran.adk.symcalc.Log
import io.github.vagran.adk.symcalc.Sin
import io.github.vagran.adk.symcalc.Variable
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import io.github.vagran.adk.symcalc.Expression as E


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
        val e = E(0.0) + 0.0 + x
        val eOpt = e.Optimize()
        assertEquals("x", eOpt.toString())
        assert(eOpt.variable == x)
    }

    @Test
    fun OptimizeSumWithConstants()
    {
        val e = E(1.0) + x + E(2.0) + y + E(3.0)
        val eOpt = e.Optimize()
        assertEquals("x + y + 6.0", eOpt.toString())
        assert(x === eOpt.funcArgs!![0].variable)
        assert(y === eOpt.funcArgs!![1].variable)
        assertEquals(6.0, eOpt.funcArgs!![2].constant)
    }

    @Test
    fun OptimizeProductWithOne()
    {
        val e = E(1.0) * x * 1.0 * y * 2.0 * 1.0 * 1.0
        val eOpt = e.Optimize()
        assertEquals("x * y * 2.0", eOpt.toString())
        /* Check sum promotion as well */
        assert(x === eOpt.funcArgs!![0].variable)
        assert(y === eOpt.funcArgs!![1].variable)
        assertEquals(2.0, eOpt.funcArgs!![2].constant)
    }

    @Test
    fun OptimizeProductWithZero()
    {
        val e = E(1.0) * x * 1.0 * y * 0.0 * 1.0 * 1.0
        val eOpt = e.Optimize()
        assertEquals("0.0", eOpt.toString())
        assertEquals(0.0, eOpt.constant)
    }

    @Test
    fun OptimizeDegenerateProduct()
    {
        val e = E(1.0) * 1.0 * x
        val eOpt = e.Optimize()
        assertEquals("x", eOpt.toString())
        assert(eOpt.variable == x)
    }

    @Test
    fun OptimizeProductWithConstants()
    {
        val e = E(2.0) * x * E(3.0) * y * E(4.0)
        val eOpt = e.Optimize()
        assertEquals("x * y * 24.0", eOpt.toString())
        assert(x === eOpt.funcArgs!![0].variable)
        assert(y === eOpt.funcArgs!![1].variable)
        assertEquals(24.0, eOpt.funcArgs!![2].constant)
    }

    @Test
    fun OptimizePower()
    {
        val e = (E(0.0) pow x) + (E(1.0) pow y) + (x pow 0.0) + (y pow 1.0)
        val eOpt = e.Optimize()
        assertEquals("2.0 + y", eOpt.toString())
        assertEquals(2.0, eOpt.funcArgs!![0].constant)
        assert(y === eOpt.funcArgs!![1].variable)
    }


    @Test
    fun OptimizePowerProduct()
    {
        val e = x * (x pow y) * (y pow x) * (x pow 3.0) * (y pow 5.0) * x / y * z * Sin(z) * Sin(z)
        val eOpt = e.Optimize()
        assertEquals("sin(z)^2.0 * y^(x + 4.0) * x^(y + 5.0) * z", eOpt.toString())
    }

    @Test
    fun OptimizePowerLogarithm()
    {
        val e = Log(x pow y)
        val eOpt = e.Optimize()
        assertEquals("y * log(x)", eOpt.toString())
    }

    @Test
    fun OptimizeExponentLogarithm()
    {
        val e = Log(Exp(x))
        val eOpt = e.Optimize()
        assertEquals("x", eOpt.toString())
    }

    @Test
    fun OptimizeLogarithmExponent()
    {
        val e = Exp(Log(x * y))
        val eOpt = e.Optimize()
        assertEquals("x * y", eOpt.toString())
    }

    @Test
    fun OptimizeSum()
    {
        val e = E(0.0) + E(3.0) * (y pow 0.0) * 4.0 + E(1.0) + E(0.0) + E(2.0) * x -
            (x pow 1.0) + E(2.0) + E(3.0) * (x pow 2.0) * 2.0 + (x pow 2.0) * 2.0 + E(0.0) + E(5.0)
        println(e.Optimize().toString())
    }

    @Test
    fun NonCommutativeEquals()
    {
        assert(E(x) pow y == E(x) pow E(y))
        assert(E(x) pow y != E(y) pow E(x))
    }

    @Test
    fun CommutativeEquals()
    {
        assertEquals((E(x) + E(y) + E(1.0)).Optimize().hashCode(),
                     (E(1.0) + E(x) + E(y)).Optimize().hashCode())
        assert((E(x) + E(y) + E(1.0)).Optimize() == (E(1.0) + E(x) + E(y)).Optimize())
    }
}
