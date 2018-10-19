package coffee

import com.ast.adk.injector.Component
import com.ast.adk.injector.DI
import com.ast.adk.injector.Inject
import com.ast.adk.injector.Singleton

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

