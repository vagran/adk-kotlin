import io.github.vagran.adk.symcalc.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import io.github.vagran.adk.symcalc.Expression as E

/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class CompilerTest {

    val x = Variable("x")
    val y = Variable("y")
    val z = Variable("z")

    @Test
    fun Basic()
    {
        val e1 = E(1.0) + x * 2.0 + (y pow 2.0) * Sin(z) * 5.0 + (Cos(z) pow 2.0) * 3.0
        val e2 = (x pow 3.0) - y + Cos(z)
        val compiler = Compiler()
        val e1Res = ResultHandle()
        val e2Res = ResultHandle()
        compiler.AddExpression(e1.Optimize(), e1Res)
        compiler.AddExpression(e2.Optimize(), e2Res)
        val program = compiler.Compile()

        //val ctx = ExecutionContext()
        //program.Execute(ctx)
    }

}
