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
