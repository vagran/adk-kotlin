/*
 * This file is part of ADK project.
 * Copyright (c) 2020 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package coffee

import io.github.vagran.adk.injector.Provides
import io.github.vagran.adk.injector.Singleton
import io.github.vagran.adk.injector.Module


@Module(include = [PumpModule::class])
class DripCoffeeModule {

    @Provides
    @Singleton
    fun provideHeater(): Heater
    {
        return ElectricHeater()
    }
}
