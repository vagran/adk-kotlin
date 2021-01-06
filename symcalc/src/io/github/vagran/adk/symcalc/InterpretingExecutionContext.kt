/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

/** Execution context which interprets the program and evaluates results. Mostly useful for testing.
  */
class InterpretingExecutionContext(private val evalCtx: EvaluationContext): ExecutionContext {

    val results = HashMap<ResultHandle, Double>()

    override fun SetLiterals(literals: DoubleArray)
    {
        this.literals = literals
    }

    override fun SetLocalsCount(n: Int)
    {
        locals = DoubleArray(n)
    }

    override fun SetStackDepth(n: Int)
    {
        stack = DoubleArray(n)
    }

    override fun LoadLiteral(idx: Int)
    {
        stack[sp++] = literals[idx]
    }

    override fun LoadVariable(v: Variable)
    {
        stack[sp++] = evalCtx.GetVariable(v)
    }

    override fun LoadLocal(idx: Int)
    {
        stack[sp++] = locals[idx]
    }

    override fun StoreResult(result: ResultHandle)
    {
        results[result] = stack[sp - 1]
    }

    override fun StoreLocal(idx: Int)
    {
        locals[idx] = stack[sp - 1]
    }

    override fun Pop()
    {
        sp--
    }

    override fun ApplyFunction(func: Function, numArgs: Int)
    {
        val args = DoubleArray(numArgs) { stack[sp - numArgs + it] }
        sp -= numArgs
        stack[sp++] = func.Evaluate(args)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private lateinit var literals: DoubleArray
    private lateinit var locals: DoubleArray
    private lateinit var stack: DoubleArray
    private var sp = 0
}
