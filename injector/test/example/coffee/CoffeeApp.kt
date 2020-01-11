package coffee

import io.github.vagran.adk.injector.Component
import io.github.vagran.adk.injector.DI
import io.github.vagran.adk.injector.Inject
import io.github.vagran.adk.injector.Singleton

object CoffeeApp {

    @Singleton
    @Component(modules = [DripCoffeeModule::class])
    class CoffeeShop {
        @Inject
        lateinit var maker: CoffeeMaker
    }

    @JvmStatic
    fun main(args: Array<String>)
    {
        val coffeeShop = DI.CreateComponent(CoffeeShop::class)
        coffeeShop.maker.brew()
    }
}

