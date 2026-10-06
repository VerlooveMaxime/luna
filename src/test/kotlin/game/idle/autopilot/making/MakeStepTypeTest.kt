package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MakeStepTypeTest {

    private val dough = Recipe(2307, "bread dough", 1933, 1929)
    private val unf = Recipe(91, "guam potion (unf)", 227, 249)
    private val make = MakeStepType(RecipeCatalog(listOf(dough, unf)))

    @Test
    fun `a make line names its product, as many as possible`() {
        assertEquals(listOf("bread dough", "all"), make.parse(listOf("bread", "dough")))
        assertEquals("make bread dough", make.line(listOf("bread dough", "all")))
    }

    @Test
    fun `a make line may start with a count`() {
        assertEquals(listOf("guam potion (unf)", "5"), make.parse(listOf("5", "guam", "potion", "(unf)")))
        assertEquals("make 5 guam potion (unf)", make.line(listOf("guam potion (unf)", "5")))
    }

    @Test
    fun `make without a product`() {
        assertRejected("make needs a product: make [<n>] <product>") { make.parse(listOf("3")) }
    }

    @Test
    fun `a make step makes its recipe, whatever the case`() {
        assertEquals(MakeStep(dough, amount = 2), make.resolve(listOf("Bread Dough", "2"), FlowContext()))
        assertEquals(MakeStep(unf, amount = null), make.resolve(listOf("guam potion (unf)", "all"), FlowContext()))
    }

    @Test
    fun `a product without a recipe lists the ones there are`() {
        assertRejected("Nothing called 'cake' can be made yet. Products: bread dough, guam potion (unf)") {
            make.resolve(listOf("cake", "all"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the products and the amounts`() {
        assertEquals(listOf("bread dough", "guam potion (unf)"), make.fields[0].choices(emptyList()))
        assertEquals(listOf("all", "1", "5", "10"), make.fields[1].choices(emptyList()))
    }

    @Test
    fun `the steps after a make step know it gathers its product`() {
        assertEquals(FlowContext(gathered = setOf(1511, 2307)), MakeStep(dough).after(FlowContext(gathered = setOf(1511))))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
