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
