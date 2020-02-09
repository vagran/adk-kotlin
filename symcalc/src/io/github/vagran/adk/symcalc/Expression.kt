/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.symcalc

class Expression {
    /** Literal constant. */
    constructor(c: Double)

    /** Variable reference. */
    constructor(v: Variable)

    /** Function node. */
    constructor(f: IFunction, vararg params: Expression)
}
