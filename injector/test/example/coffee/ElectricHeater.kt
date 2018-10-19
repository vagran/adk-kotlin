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
