/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

open class Variable(val name: String) {

    override fun toString(): String
    {
        return name
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    // Operators

    operator fun unaryMinus() = Expression(-1.0) * this

    operator fun times(e: Expression) = Expression(this) * e

    operator fun times(v: Double) = Expression(this) * v

    operator fun times(v: Variable) = Expression(this) * v

    operator fun plus(e: Expression) = Expression(this) + e

    operator fun plus(v: Double) = Expression(this) + v

    operator fun plus(v: Variable) = Expression(this) + v

    operator fun minus(e: Expression) = Expression(this) - e

    operator fun minus(v: Double) = Expression(this) - v

    operator fun minus(v: Variable) = Expression(this) - v

    operator fun div(e: Expression) = Expression(this) / e

    operator fun div(v: Double) = Expression(this) / v

    operator fun div(v: Variable) = Expression(this) / v

    infix fun pow(e: Expression) = Expression(this) pow e

    infix fun pow(v: Double) = Expression(this) pow v

    infix fun pow(v: Variable) = Expression(this) pow v
}
