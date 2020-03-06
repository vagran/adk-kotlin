/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

/** Represents a compiled program for expression evaluation. This is intermediate representation
 * which is typically used for further byte-code or any other final representation generation.
 */
class Program {

    /** Traverses the program flow so that it can be transformed by the provided execution context.
     */
    fun Execute(ctx: ExecutionContext)
    {
        ctx.SetLiterals(literals)
        var intIdx = 0
        var funcIdx = 0
        var varIdx = 0
        var resultIdx = 0
        for (op in operations) {
            when (op) {
                Operation.LOAD_LITERAL -> ctx.LoadLiteral(intArgs[intIdx++])

                Operation.LOAD_VARIABLE -> ctx.LoadVariable(varArgs[varIdx++])

                Operation.LOAD_LOCAL -> ctx.LoadLocal(intArgs[intIdx++])

                Operation.STORE_RESULT -> ctx.StoreResult(resultArgs[resultIdx++])

                Operation.STORE_LOCAL -> ctx.StoreLocal(intArgs[intIdx++])

                Operation.POP -> ctx.Pop()

                Operation.APPLY_FUNCTION -> ctx.ApplyFunction(funcArgs[funcIdx++], intArgs[intIdx++])
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    internal enum class Operation {
        /** Load literal on the stack top.
         * Arg: literal index.
         */
        LOAD_LITERAL,
        /** Load variable on the stack top.
         * Arg: variable
         */
        LOAD_VARIABLE,
        /** Load local on the stack top.
         * Arg: local index.
         */
        LOAD_LOCAL,
        /** Store result from the stack top. Stack is not modified.
         * Arg: result handle.
         */
        STORE_RESULT,
        /** Store local from the stack top. Stack is not modified.
         * Arg: local index.
         */
        STORE_LOCAL,
        /** Remove one item from the stack top. */
        POP,
        /** Apply function to the stack top items. The arguments are removed from the stack and
         * result is placed instead.
         * Arg: number of arguments, function to apply.
         */
        APPLY_FUNCTION
    }

    internal val operations = ArrayList<Operation>()
    internal val intArgs = ArrayList<Int>()
    internal val funcArgs = ArrayList<Function>()
    internal val varArgs = ArrayList<Variable>()
    internal val resultArgs = ArrayList<ResultHandle>()
    internal lateinit var literals: DoubleArray

    internal fun AddOperation(op: Operation, vararg args: Any)
    {
        //XXX count stack depth
        operations.add(op)
        for (arg in args) {
            when (arg) {
                is Int -> intArgs.add(arg)
                is Function -> funcArgs.add(arg)
                is Variable -> varArgs.add(arg)
                is ResultHandle -> resultArgs.add(arg)
                else -> throw Error("Illegal argument type: ${arg::class}")
            }
        }
    }
}
