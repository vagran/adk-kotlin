/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

import kotlin.math.*

/** @param arity Function arity, -1 for unbound.*/
abstract class Function(val arity: Int = 1) {
    abstract fun ToString(args: Array<Expression>): String

    abstract fun Evaluate(args: DoubleArray): Double

    operator fun invoke(vararg args: Expression): Expression
    {
        return Expression(this, *args)
    }

    operator fun invoke(v: Variable): Expression
    {
        return Expression(this, Expression(v))
    }

    protected fun ToString(name: String, arg: Expression): String
    {
        val sb = StringBuilder()
        sb.append(name)
        sb.append("(")
        sb.append(arg.toString())
        sb.append(")")
        return sb.toString()
    }
}

object Add: Function(-1) {

    override fun Evaluate(args: DoubleArray): Double
    {
        var sum = 0.0
        for (x in args) {
            sum += x
        }
        return sum
    }

    override fun ToString(args: Array<Expression>): String
    {
        val sb = StringBuilder()
        var isFirst = true
        for (arg in args) {
            if (isFirst) {
                isFirst = false
            } else {
                sb.append(" + ")
            }
            sb.append(arg.toString())
        }
        return sb.toString()
    }
}

object Mul: Function(-1) {

    override fun Evaluate(args: DoubleArray): Double
    {
        var product = 1.0
        for (x in args) {
            product *= x
        }
        return product
    }

    override fun ToString(args: Array<Expression>): String
    {
        val sb = StringBuilder()
        var isFirst = true
        for (arg in args) {
            if (isFirst) {
                isFirst = false
            } else {
                sb.append(" * ")
            }
            val needParenthesis = arg.function === Add
            if (needParenthesis) {
                sb.append("(")
            }
            sb.append(arg.toString())
            if (needParenthesis) {
                sb.append(")")
            }
        }
        return sb.toString()
    }
}

object Pow: Function(2) {

    override fun Evaluate(args: DoubleArray): Double
    {
        return args[0].pow(args[1])
    }

    override fun ToString(args: Array<Expression>): String
    {
        val sb = StringBuilder()
        val baseCompound = IsCompound(args[0])
        if (baseCompound) {
            sb.append("(")
        }
        sb.append(args[0].toString())
        if (baseCompound) {
            sb.append(")")
        }
        sb.append("^")
        val expCompound = IsCompound(args[1])
        if (expCompound) {
            sb.append("(")
        }
        sb.append(args[1].toString())
        if (expCompound) {
            sb.append(")")
        }
        return sb.toString()
    }

    private fun IsCompound(e: Expression): Boolean
    {
        return e.function === Add || e.function === Mul || e.function === Pow
    }
}


object Exp: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return exp(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("exp", args[0])
}

object Log: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return ln(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("log", args[0])
}

object Sin: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return sin(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("sin", args[0])
}

object Cos: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return cos(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("cos", args[0])
}
