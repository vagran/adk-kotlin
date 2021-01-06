/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

interface ExecutionContext {
    /** Set literals pool. They are referenced by index later. */
    fun SetLiterals(literals: DoubleArray)

    /** Set total number of local slots used in the program. */
    fun SetLocalsCount(n: Int)

    /** Set maximal stack depth used in the program. */
    fun SetStackDepth(n: Int)

    /** Load literal on the stack top. */
    fun LoadLiteral(idx: Int)

    /** Load variable on the stack top. */
    fun LoadVariable(v: Variable)

    /** Load local on the stack top. */
    fun LoadLocal(idx: Int)

    /** Store result from the stack top. Stack is not modified. */
    fun StoreResult(result: ResultHandle)

    /** Store local from the stack top. Stack is not modified. */
    fun StoreLocal(idx: Int)

    /** Remove one item from the stack top. */
    fun Pop()

    /** Apply function to the stack top items. The arguments are removed from the stack and
     * result is placed instead.
     */
    fun ApplyFunction(func: Function, numArgs: Int)
}
