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
        //XXX
    }
}
