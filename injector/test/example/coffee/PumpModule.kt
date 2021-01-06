/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package coffee

import io.github.vagran.adk.injector.Provides
import io.github.vagran.adk.injector.Module

@Module
class PumpModule {

    @Provides
    fun providePump(pump: Thermosiphon): Pump
    {
        return pump
    }
}
