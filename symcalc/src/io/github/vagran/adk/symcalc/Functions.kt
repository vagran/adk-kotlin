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

    /** Default implementation accounts arguments order. */
    open fun HashCode(args: Array<Expression>): Int
    {
        var h = hashCode()
        for (e in args) {
            h *= 31
            h += e.hashCode()
        }
        return h
    }

    open fun Equals(args1: Array<Expression>, args2: Array<Expression>): Boolean
    {
        if (args1.size != args2.size) {
            return false
        }
        for (i in args1.indices) {
            if (args1[i] != args2[i]) {
                return false
            }
        }
        return true
    }

    open fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        if (args.size != 1) {
            /* Derived classes with different number of arguments should implement this method. */
            throw NotImplementedError()
        }
        val arg = args[0]
        if (arg.variable === dv) {
            return Derivative(arg)
        }
        return Derivative(arg) * arg.Derivative(dv)
    }

    override fun toString(): String
    {
        return this::class.simpleName!!
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    protected fun ToString(name: String, arg: Expression): String
    {
        val sb = StringBuilder()
        sb.append(name)
        sb.append("(")
        sb.append(arg.toString())
        sb.append(")")
        return sb.toString()
    }

    protected fun CommutativeHashCode(args: Array<Expression>): Int
    {
        var h = hashCode()
        for (e in args) {
            h = h xor e.hashCode()
        }
        return h
    }

    protected fun CommutativeEquals(args1: Array<Expression>, args2: Array<Expression>): Boolean
    {
        data class Item(var count: Int = 0)

        val args1Map = HashMap<Expression, Item>()
        val args2Map = HashMap<Expression, Item>()
        for (e in args1) {
            val item = args1Map.computeIfAbsent(e) { Item() }
            item.count++
        }
        for (e in args2) {
            val item = args2Map.computeIfAbsent(e) { Item() }
            item.count++
        }
        return args1Map == args2Map
    }

    /** One argument function derivative with respect to the argument. */
    protected open fun Derivative(arg: Expression): Expression
    {
        throw NotImplementedError()
    }
}

/** Custom symbol which is preserved in a transformed expression. */
open class Symbol(val name: String): Function(0) {

    override fun ToString(args: Array<Expression>): String
    {
        return name
    }

    override fun Evaluate(args: DoubleArray): Double
    {
        throw Error("Symbol cannot be evaluated: $name")
    }

    override fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        return Expression(SymbolDerivative(this, dv))
    }
}

/** Derivative of a symbol with respect to the specified variable. They can be chained for higher
 * order derivative (possibly with respect to different variables).
  */
class SymbolDerivative(val sym: Symbol, val dv: Variable): Symbol(sym.name) {

    override fun ToString(args: Array<Expression>): String
    {
        return "${sym.ToString(emptyArray())}'($dv)"
    }

    override fun Evaluate(args: DoubleArray): Double
    {
        throw Error("Symbol cannot be evaluated: " + ToString(emptyArray()))
    }

    override fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        return Expression(SymbolDerivative(this, dv))
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

    /** Insensitive to arguments order. */
    override fun HashCode(args: Array<Expression>): Int
    {
        return CommutativeHashCode(args)
    }

    override fun Equals(args1: Array<Expression>, args2: Array<Expression>): Boolean
    {
        return CommutativeEquals(args1, args2)
    }

    override fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        return Expression(Add, *Array(args.size) { idx -> args[idx].Derivative(dv) })
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

    /** Insensitive to arguments order. */
    override fun HashCode(args: Array<Expression>): Int
    {
        return CommutativeHashCode(args)
    }

    override fun Equals(args1: Array<Expression>, args2: Array<Expression>): Boolean
    {
        return CommutativeEquals(args1, args2)
    }

    override fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        val sum = Array(args.size) {
            idx ->
            val product = Array(args.size) {
                mulIdx ->
                if (mulIdx == idx) {
                    args[mulIdx].Derivative(dv)
                } else {
                    args[mulIdx]
                }
            }
            Expression(Mul, *product)
        }
        return Expression(Add, *sum)
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

    override fun Derivative(dv: Variable, args: Array<Expression>): Expression
    {
        val base = args[0]
        val exp = args[1]
        val dBase = base.HasVariable(dv)
        val dExp = exp.HasVariable(dv)

        if (dBase && !dExp) {
            return exp * (base pow (exp - 1.0)) * base.Derivative(dv)
        }
        if (dExp && !dBase) {
            return (base pow exp) * Log(base) * exp.Derivative(dv)
        }
        if (dBase && dExp) {
            val transformed = Exp(exp * Log(base))
            return transformed.Derivative(dv)
        }
        return Expression.ZERO
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

    override fun Derivative(arg: Expression): Expression
    {
        return Exp(arg)
    }
}

object Log: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return ln(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("log", args[0])

    override fun Derivative(arg: Expression): Expression
    {
        return arg pow -1.0
    }
}

object Sin: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return sin(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("sin", args[0])

    override fun Derivative(arg: Expression): Expression
    {
        return Cos(arg)
    }
}

object Cos: Function() {

    override fun Evaluate(args: DoubleArray): Double
    {
        return cos(args[0])
    }

    override fun ToString(args: Array<Expression>) = ToString("cos", args[0])

    override fun Derivative(arg: Expression): Expression
    {
        return -Sin(arg)
    }
}
