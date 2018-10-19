package coffee

import com.ast.adk.injector.Inject

class Thermosiphon
    @Inject constructor(private val heater: Heater): Pump {

    override fun pump()
    {
        if (heater.isHot) {
            println("=> => pumping => =>")
        }
    }
}
