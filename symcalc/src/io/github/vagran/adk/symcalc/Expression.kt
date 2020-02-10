/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

/** Represents symbolic expression. Immutable. */
class Expression {
    /** Literal constant. */
    constructor(c: Double)
    {
        constant = c
        isConst = true
        variable = null
        function = null
        funcArgs = null
    }

    /** Variable reference. */
    constructor(v: Variable)
    {
        variable = v
        isConst = false
        constant = null
        function = null
        funcArgs = null
    }

    /** Function node. */
    constructor(f: Function, vararg args: Expression)
    {
        if (args.size < f.minArity) {
            throw IllegalArgumentException("Function should have at least ${f.minArity} " +
                                           "arguments, have ${args.size}")
        }
        if (f.maxArity != -1 && args.size > f.maxArity) {
            throw IllegalArgumentException("Function should have at most ${f.maxArity} " +
                                           "arguments, have ${args.size}")
        }
        function = f
        @Suppress("UNCHECKED_CAST")
        funcArgs = args as Array<Expression>
        constant = null
        variable = null
        isConst = run {
            for (e in args) {
                if (!e.isConst) {
                    return@run false
                }
            }
            true
        }
    }

    val constant: Double?
    val variable: Variable?
    val function: Function?
    val funcArgs: Array<Expression>?

    /** True if expression value is constant. */
    val isConst: Boolean

    /**@return true if expression contains the specified variable. */
    fun HasVariable(v: Variable): Boolean
    {
        if (constant != null) {
            return false
        }
        if (variable != null) {
            return variable === v
        }
        funcArgs!!.forEach {
            if (it.HasVariable(v)) {
                return true
            }
        }
        return false
    }

    /** Try to simplify and optimize the expression. */
    fun Optimize(): Expression
    {
        //XXX
        return this
    }

    fun Evaluate(ctx: EvaluationContext): Double
    {
        //XXX
        return 0.0
    }

    override fun toString(): String
    {
        if (constant != null) {
            return constant.toString()
        }
        if (variable != null) {
            return variable.toString()
        }
        return function!!.ToString(funcArgs!!)
    }
}
