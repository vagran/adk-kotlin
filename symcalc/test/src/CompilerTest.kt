/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */
import io.github.vagran.adk.symcalc.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.PrintStream
import io.github.vagran.adk.symcalc.Expression as E


class NamedResultHandle(val name: String): ResultHandle() {
    override fun toString() = name
}

class SimpleEvaluationContext: EvaluationContext {
    override fun GetVariable(v: Variable): Double
    {
        return vars[v]!!
    }

    fun SetVariable(v: Variable, value: Double)
    {
        vars[v] = value
    }

    private val vars = HashMap<Variable, Double>()
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class CompilerTest {

    val x = Variable("x")
    val y = Variable("y")
    val z = Variable("z")

    @Test
    fun Basic()
    {
        val e1 = E(1.0) + x * 2.0 + (y pow 2.0) * Sin(z) * 5.0 + (Cos(z) pow 2.0) * 3.0
        val e2 = (x pow 3.0) - y + Cos(z) + (x pow x) + Sin(z) * Sin(z)
        val e3 = e1.Optimize() * 2.0

        val compiler = Compiler()
        val e1Res = NamedResultHandle("e1")
        val e2Res = NamedResultHandle("e2")
        val e3Res = NamedResultHandle("e3")
        compiler.AddExpression(e1.Optimize(), e1Res)
        compiler.AddExpression(e2.Optimize(), e2Res)
        compiler.AddExpression(e3.Optimize(), e3Res)
        val program = compiler.Compile()

        val ctx = PrintExecutionContext(PrintStream(System.out))
        program.Execute(ctx)

        val evlCtx = SimpleEvaluationContext()
        evlCtx.SetVariable(x, 2.0)
        evlCtx.SetVariable(y, 3.0)
        evlCtx.SetVariable(z, Math.PI / 2.0)
        val interpCtx = InterpretingExecutionContext(evlCtx)
        program.Execute(interpCtx)

        assertEquals(50.0, interpCtx.results[e1Res])
        assertEquals(10.0, interpCtx.results[e2Res])
        assertEquals(100.0, interpCtx.results[e3Res])
    }
}
