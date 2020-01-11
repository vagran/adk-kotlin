/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package coffee

import io.github.vagran.adk.injector.Inject

class CoffeeMaker
    @Inject constructor(private val heater: Heater,
                        private val pump: Pump) {

    fun brew()
    {
        heater.on()
        pump.pump()
        println(" [_]P coffee! [_]P ")
        heater.off()
    }
}
