/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

import java.io.PrintStream

class PrintExecutionContext(private val out: PrintStream): ExecutionContext {

    override fun SetLiterals(literals: DoubleArray)
    {
        this.literals = literals
    }

    override fun LoadLiteral(idx: Int)
    {
        out.printf("Load literal #%d: %g\n", idx, literals[idx])
        out.flush()
    }

    override fun LoadVariable(v: Variable)
    {
        out.printf("Load variable %s\n", v.toString())
        out.flush()
    }

    override fun LoadLocal(idx: Int)
    {
        out.printf("Load local #%d\n", idx)
        out.flush()
    }

    override fun StoreResult(result: ResultHandle)
    {
        out.printf("Store result %s\n", result.toString())
        out.flush()
    }

    override fun StoreLocal(idx: Int)
    {
        out.printf("Store local #%d\n", idx)
        out.flush()
    }

    override fun Pop()
    {
        out.println("Pop")
        out.flush()
    }

    override fun ApplyFunction(func: Function, numArgs: Int)
    {
        out.printf("Apply function %s on %d arguments\n", func.toString(), numArgs)
        out.flush()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var literals: DoubleArray
}
