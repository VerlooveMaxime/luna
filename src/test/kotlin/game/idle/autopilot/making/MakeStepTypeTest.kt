package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MakeStepTypeTest {

    private val dough = Recipe(2307, "bread dough", 1933, 1929)
    private val unf = Recipe(91, "guam potion (unf)", 227, 249)
    private val make = MakeStepType(RecipeCatalog(listOf(dough, unf)))

    private fun settings(vararg values: Pair<String, String>) = StepSettings("make", mapOf(*values))

    private fun choices(index: Int) = (make.fields[index] as StepField.Choice).choices(settings())

    @Test
    fun `a make step reads as its product, its count written when it has one`() {
        assertEquals("make bread dough", make.summary(settings("product" to "bread dough")))
        assertEquals("make 5 guam potion (unf)", make.summary(settings("product" to "guam potion (unf)", "amount" to "5")))
    }

    @Test
    fun `a make step without a product reads with a question mark and is rejected`() {
        assertEquals("make ?", make.summary(settings()))
        assertRejected("make needs a product") { make.resolve(settings(), FlowContext()) }
    }

    @Test
    fun `a make step makes its recipe, whatever the case`() {
        assertEquals(MakeStep(dough, amount = 2), make.resolve(settings("product" to "Bread Dough", "amount" to "2"), FlowContext()))
        assertEquals(MakeStep(unf, amount = null), make.resolve(settings("product" to "guam potion (unf)"), FlowContext()))
    }

    @Test
    fun `a product without a recipe lists the ones there are`() {
        assertRejected("Nothing called 'cake' can be made yet. Products: bread dough, guam potion (unf)") {
            make.resolve(settings("product" to "cake"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the products and the amounts`() {
        assertEquals(listOf("bread dough", "guam potion (unf)"), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
    }

    @Test
    fun `the steps after a make step know it gathers its product`() {
        assertEquals(FlowContext(gathered = setOf(1511, 2307)), MakeStep(dough).after(FlowContext(gathered = setOf(1511))))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
