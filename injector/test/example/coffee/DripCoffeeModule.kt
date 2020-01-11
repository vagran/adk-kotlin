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
