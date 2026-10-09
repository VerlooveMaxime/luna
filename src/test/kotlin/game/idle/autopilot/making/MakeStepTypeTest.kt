package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MakeStepTypeTest {

    private val dough = BREAD_DOUGH
    private val unf = simpleRecipe(91, "Guam potion (unf)", 227, 249)
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
    fun `a product without a recipe is rejected`() {
        assertRejected("Nothing called 'cake' can be made.") {
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

    @Test
    fun `the steps after a make step know every item it counts as made`() {
        val wine = simpleRecipe(1995, "Unfermented wine", 1987, 1937).copy(made = setOf(1995, 1993))

        assertEquals(FlowContext(gathered = setOf(1995, 1993)), MakeStep(wine).after(FlowContext()))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    @Test
    fun `a make step shows its recipe's skill`() {
        assertEquals(StepIcon.Skill(Skill.COOKING), make.icon(settings("product" to "bread dough")))
    }

    @Test
    fun `a make step shows Crafting until a product is picked`() {
        assertEquals(StepIcon.Skill(Skill.CRAFTING), make.icon(settings()))
    }

    @Test
    fun `a make step shows Crafting for a product nothing makes`() {
        assertEquals(StepIcon.Skill(Skill.CRAFTING), make.icon(settings("product" to "gold")))
    }
}
