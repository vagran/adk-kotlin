package coffee

import com.ast.adk.injector.Provides
import com.ast.adk.injector.Singleton
import com.ast.adk.injector.Module


@Module(include = [PumpModule::class])
class DripCoffeeModule {

    @Provides
    @Singleton
    fun provideHeater(): Heater
    {
        return ElectricHeater()
    }
}
