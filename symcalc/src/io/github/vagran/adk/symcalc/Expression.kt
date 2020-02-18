/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

import io.github.vagran.adk.symcalc.optimization.Rule

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
        if (f.arity != -1 && args.size != f.arity) {
            throw IllegalArgumentException("Function should have ${f.arity} " +
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
        var thisExpr = this
        while (true) {
            if (thisExpr.function == null) {
                return thisExpr
            }
            if (thisExpr.isConst) {
                return Expression(thisExpr.Evaluate(ConstantEvaluationContext))
            }
            var newArgs: Array<Expression>? = null
            thisExpr.funcArgs!!.forEachIndexed { idx, e ->
                val newExpr = e.Optimize()
                if (newExpr !== e) {
                    if (newArgs == null) {
                        newArgs = thisExpr.funcArgs!!.copyOf()
                    }
                    newArgs!![idx] = newExpr
                }
            }
            if (newArgs != null) {
                thisExpr = Expression(thisExpr.function!!, *newArgs!!)
            }

            var newExpr: Expression? = null
            for (rule in Rule.builtinRules) {
                val m = rule.Match(thisExpr)
                if (m != null) {
                    newExpr = rule.Optimize(thisExpr, m)
                    break
                }
            }
            if (newExpr != null) {
                thisExpr = newExpr
            } else {
                break
            }
        }
        return thisExpr
    }

    fun Evaluate(ctx: EvaluationContext): Double
    {
        if (constant != null) {
            return constant
        }
        if (variable != null) {
            return ctx.GetVariable(variable)
        }
        val args = DoubleArray(funcArgs!!.size) {
            i ->
            funcArgs[i].Evaluate(ctx)
        }
        return function!!.Evaluate(args)
    }

    /** Get derivative with respect to the specified variable. */
    fun Derivative(dv: Variable): Expression
    {
        //XXX
        return this
    }

    override fun toString(): String
    {
        if (constant != null) {
            if (constant < 0) {
                return "($constant)"
            }
            return constant.toString()
        }
        if (variable != null) {
            return variable.toString()
        }
        return function!!.ToString(funcArgs!!)
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Operators

    operator fun unaryMinus(): Expression
    {
        return Expression(-1.0) * this
    }

    operator fun times(e: Expression): Expression
    {
        return Expression(Mul, this, e)
    }

    operator fun times(v: Double): Expression
    {
        return Expression(Mul, this, Expression(v))
    }

    operator fun times(v: Variable): Expression
    {
        return Expression(Mul, this, Expression(v))
    }

    operator fun plus(e: Expression): Expression
    {
        return Expression(Add, this, e)
    }

    operator fun plus(v: Double): Expression
    {
        return Expression(Add, this, Expression(v))
    }

    operator fun plus(v: Variable): Expression
    {
        return Expression(Add, this, Expression(v))
    }

    operator fun minus(e: Expression): Expression
    {
        return Expression(Add, this, e * -1.0)
    }

    operator fun minus(v: Double): Expression
    {
        return Expression(Add, this, Expression(-v))
    }

    operator fun minus(v: Variable): Expression
    {
        return Expression(Add, this, -v)
    }

    operator fun div(e: Expression): Expression
    {
        return Expression(Mul, this, Expression(Pow, e, Expression(-1.0)))
    }

    operator fun div(v: Double): Expression
    {
        return Expression(Mul, this, Expression(1.0 / v))
    }

    operator fun div(v: Variable): Expression
    {
        return Expression(Mul, this, Expression(Pow, Expression(v), Expression(-1.0)))
    }

    infix fun pow(e: Expression): Expression
    {
        return Expression(Pow, this, e)
    }

    infix fun pow(v: Double): Expression
    {
        return Expression(Pow, this, Expression(v))
    }

    infix fun pow(v: Variable): Expression
    {
        return Expression(Pow, this, Expression(v))
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
}
