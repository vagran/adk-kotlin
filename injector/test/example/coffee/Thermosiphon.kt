/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package coffee

import io.github.vagran.adk.injector.Inject

class Thermosiphon
    @Inject constructor(private val heater: Heater): Pump {

    override fun pump()
    {
        if (heater.isHot) {
            println("=> => pumping => =>")
        }
    }
}
