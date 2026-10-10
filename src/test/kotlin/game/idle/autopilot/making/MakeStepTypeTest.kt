package game.idle.autopilot.making

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.ToolNeed
import game.idle.flow.Uses
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MakeStepTypeTest {

    private val dough = BREAD_DOUGH
    private val unf = simpleRecipe(91, "Guam potion (unf)", 227, 249)
    private val flour = 1933
    private val water = 1929
    private val jug = 1937
    private val twoWays = simpleRecipe(1953, "Pastry dough", flour, water).let { it.copy(ways = it.ways + RecipeWay(flour, jug, mapOf(flour to 1, jug to 1))) }
    private val names = FakeNames(items = mapOf(flour to "Pot of flour", water to "Bucket of water", jug to "Jug of water"))
    private val make = MakeStepType(RecipeCatalog(listOf(dough, unf, twoWays)), names)
    private val flourMilled = FlowContext(gathered = setOf(flour), gatheredBy = mapOf(flour to 1))

    private fun settings(vararg values: Pair<String, String>) = StepSettings("make", mapOf(*values))

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
    fun `the configure screen toggles the input, searches the product, notes what it takes and types the amount`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Product (left): search product, 'What would you like to make?'",
                "Uses (left): note",
                "Amount (left): typed amount 1..2147483647, button 'All'",
            ),
            described(make.fields(FakeNames())),
        )
    }

    @Test
    fun `the uses note names the items of the product's way`() {
        assertEquals("Pot of flour + bucket of water", uses(settings("product" to "bread dough")))
    }

    @Test
    fun `the uses note says when a product has other ways`() {
        assertEquals("Pot of flour + bucket of water (or another way)", uses(settings("product" to "pastry dough")))
    }

    @Test
    fun `the uses note waits for a product`() {
        assertEquals(Uses.NOTHING_PICKED, uses(settings()))
    }

    @Test
    fun `a make step on earlier steps names what no step before gets`() {
        assertRejected("no step before gets bucket of water") {
            make.resolve(settings("product" to "bread dough", "input" to "earlier"), flourMilled)
        }
    }

    @Test
    fun `a make step on earlier steps works with a way taking only what they get`() {
        val step = make.resolve(settings("product" to "pastry dough", "input" to "earlier"), FlowContext(gathered = setOf(flour, jug)))

        assertEquals(MakeStep(twoWays), step)
    }

    @Test
    fun `a new make step alone takes its items from the bank`() {
        assertEquals(settings("input" to "bank"), make.newSettings(FlowContext()))
    }

    @Test
    fun `a make step's input follows the flow until one is kept`() {
        assertEquals(InputSource.EARLIER_STEPS, make.input(settings(), flourMilled))
    }

    @Test
    fun `a make step's slot names the step its items come from`() {
        assertEquals(listOf("from step 1", "all of them"), make.details(settings("product" to "bread dough", "input" to "earlier"), flourMilled))
    }

    @Test
    fun `the configure screen shows the level of the recipe's skill`() {
        assertEquals(Skill.COOKING, make.skill(settings("product" to "bread dough")))
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

    @Test
    fun `a make step's target is its product among the recipes`() {
        val target = make.target(FakeNames())

        assertEquals(listOf("product", "Bread dough"), listOf(target.key, target.picked(settings("product" to "bread dough"))?.label))
    }

    @Test
    fun `a make step's slot says how many a lap makes, from the bank`() {
        assertEquals(listOf("from the bank", "5 per lap"), make.details(settings("product" to "bread dough", "amount" to "5"), FlowContext()))
    }

    @Test
    fun `a make step without a count makes all of them`() {
        assertEquals(listOf("from the bank", "all of them"), make.details(settings(), FlowContext()))
    }

    private fun uses(settings: StepSettings): String = (make.fields(names)[2] as StepField.Note).text(settings, FlowContext())

    @Test
    fun `a make step needs each way's tools and items, one entry a way`() {
        val tooled = twoWays.copy(ways = listOf(twoWays.ways.first().copy(tools = setOf(946)), twoWays.ways.last()))

        assertEquals(
            listOf(StepNeeds(listOf(ToolNeed(word = null, mapOf(946 to 1))), listOf(flour, water)), StepNeeds(emptyList(), listOf(flour, jug))),
            MakeStep(tooled).needs(),
        )
    }
}
