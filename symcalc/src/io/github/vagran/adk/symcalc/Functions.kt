/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

/** @param minArity Function minimal arity.
 * @param maxArity Function maximal arity, -1 for unlimited.
 */
abstract class Function(val minArity: Int, val maxArity: Int = minArity) {
    abstract fun ToString(args: Array<Expression>): String

    abstract fun Evaluate(args: DoubleArray): Double

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

object Add: Function(2, -1) {

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

object Mul: Function(2, -1) {

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


object Exp: Function(1) {

    override fun ToString(args: Array<Expression>) = ToString("exp", args[0])
}

object Log: Function(1) {

    override fun ToString(args: Array<Expression>) = ToString("log", args[0])
}

object Sin: Function(1) {

    override fun ToString(args: Array<Expression>) = ToString("sin", args[0])
}

object Cos: Function(1) {

    override fun ToString(args: Array<Expression>) = ToString("cos", args[0])
}
