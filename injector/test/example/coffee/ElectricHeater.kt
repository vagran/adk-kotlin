/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package coffee

class ElectricHeater: Heater {

    var heating: Boolean = false

    override fun on()
    {
        println("~ ~ ~ heating ~ ~ ~")
        heating = true
    }

    override fun off()
    {
        heating = false
    }

    override val isHot: Boolean get() = heating
}
