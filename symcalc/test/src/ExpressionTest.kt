/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

import io.github.vagran.adk.symcalc.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import io.github.vagran.adk.symcalc.Expression as E


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ExpressionTest {

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
    fun OptimizeLogarithmProductExponent()
    {
        val e = Exp(y * 2.0 * Log(x))
        val eOpt = e.Optimize()
        assertEquals("x^(y * 2.0)", eOpt.toString())
    }

    @Test
    fun OptimizeExponentPower()
    {
        val e = Exp(x) pow y
        val eOpt = e.Optimize()
        assertEquals("exp(x * y)", eOpt.toString())
    }

    @Test
    fun OptimizePowerPower()
    {
        val e = ((x pow y) pow z) pow 2.0
        val eOpt = e.Optimize()
        assertEquals("x^(y * z * 2.0)", eOpt.toString())

        val e2 = x pow (y pow z)
        val eOpt2 = e2.Optimize()
        assertEquals("x^(y^z)", eOpt2.toString())
    }

    @Test
    fun OptimizeSum()
    {
        val e = E(0.0) + E(3.0) * (y pow 0.0) * 4.0 + E(1.0) + E(0.0) + E(2.0) * x -
            (x pow 1.0) + E(2.0) + E(3.0) * (x pow 2.0) * 2.0 + (x pow 2.0) * 2.0 + E(0.0) + E(5.0)
        val eOpt = e.Optimize()
        assertEquals("8.0 * x^2.0 + x + 20.0", eOpt.toString())
    }

    @Test
    fun OptimizeSum2()
    {
        val e = Sin(x) + 1.0 + Sin(x) * 2.0 + Sin(x) + 2.0 + x
        val eOpt = e.Optimize()
        assertEquals("4.0 * sin(x) + 3.0 + x", eOpt.toString())
    }

    @Test
    fun OptimizeSum3()
    {
        val e = x * y + y * x
        val eOpt = e.Optimize()
        assertEquals("2.0 * x * y", eOpt.toString())
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

        assertEquals((E(x) * E(y) * E(2.0)).Optimize().hashCode(),
                     (E(2.0) * E(x) * E(y)).Optimize().hashCode())
        assert((E(x) * E(y) * E(2.0)).Optimize() == (E(2.0) * E(x) * E(y)).Optimize())
    }

    @Test
    fun Derivative1()
    {
        val e = E(2.0) * Sin(x) + E(3.0) * Cos(x) + 1.0 + Exp(y)
        val d = e.Derivative(x).Optimize()
        assertEquals("2.0 * cos(x) + sin(x) * (-3.0)", d.toString())
    }

    @Test
    fun Derivative2()
    {
        val e = Sin(Cos(x))
        val d = e.Derivative(x).Optimize()
        assertEquals("cos(cos(x)) * (-1.0) * sin(x)", d.toString())
    }

    @Test
    fun DerivativePow1()
    {
        val e = Sin(x) pow 3.0
        val d = e.Derivative(x).Optimize()
        assertEquals("3.0 * sin(x)^2.0 * cos(x)", d.toString())
    }

    @Test
    fun DerivativePow2()
    {
        val e = y pow Sin(x)
        val d = e.Derivative(x).Optimize()
        assertEquals("y^sin(x) * log(y) * cos(x)", d.toString())
    }

    @Test
    fun DerivativePow3()
    {
        val e = x pow x
        val d = e.Derivative(x).Optimize()
        assertEquals("x^x * (log(x) + 1.0)", d.toString())
    }

    @Test
    fun DerivativePow4()
    {
        val e = Sin(x) pow Cos(x)
        val d = e.Derivative(x).Optimize()
        assertEquals("sin(x)^cos(x) * ((-1.0) * sin(x) * log(sin(x)) + cos(x)^2.0 * sin(x)^(-1.0))",
                     d.toString())
    }
}
