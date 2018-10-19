package coffee

import com.ast.adk.injector.Provides
import com.ast.adk.injector.Module

@Module
class PumpModule {

    @Provides
    fun providePump(pump: Thermosiphon): Pump
    {
        return pump
    }
}
