package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.ToolNeed
import game.idle.flow.Uses
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmithStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3188, 3426))
    private val bronzeBar = 2349
    private val type = SmithStepType(LunaGameNames)
    private val smelted = FlowContext(gathered = setOf(bronzeBar), gatheredBy = mapOf(bronzeBar to 3))

    private fun smith(vararg values: Pair<String, String>) = StepSettings("smith", mapOf(*values))

    /** Luna's smithing items name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smith step reads as its item, its defaults left out, anything else written`() {
        assertEquals("smith bronze dagger", type.summary(smith("item" to "$BRONZE_DAGGER", "within" to "10")))
        assertEquals("smith 1 bronze dagger within 5", type.summary(smith("item" to "$BRONZE_DAGGER", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a smith step without an item reads with a question mark`() {
        assertEquals("smith ?", type.summary(smith()))
    }

    @Test
    fun `a smith step whose item is no smithing item reads as it is kept`() {
        assertEquals(listOf("smith dagger", "smith 4"), listOf(type.summary(smith("item" to "dagger")), type.summary(smith("item" to "4"))))
    }

    @Test
    fun `a smith step without an item is rejected`() {
        assertRejected("smith needs an item") { type.resolve(smith(), FlowContext()) }
    }

    @Test
    fun `a smith step works around the work spot the steps before it set, its metal and table row following from the item`() {
        val step = type.resolve(smith("item" to "$IRON_PLATEBODY", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(SmithStep(BarType.IRON, SmithingTable.PLATEBODY, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smith step without an amount makes as many as the bars allow`() {
        val step = type.resolve(smith("item" to "$BRONZE_DAGGER"), FlowContext())

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile, amount = null), step)
    }

    @Test
    fun `an item no table row smiths is rejected`() {
        assertRejected("'4' is not an item to smith") { type.resolve(smith("item" to "4"), FlowContext()) }
        assertRejected("'dagger' is not an item to smith") { type.resolve(smith("item" to "dagger"), FlowContext()) }
    }

    @Test
    fun `the configure screen toggles the input, searches the item, notes its bars, types the amount and the radius`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Item (left): search item, 'What would you like to smith?'",
                "Uses (left): note",
                "Amount (left): typed amount 1..2147483647, button 'All'",
                "Within (right): typed within 1..32",
            ),
            described(type.fields(FakeNames())),
        )
    }

    @Test
    fun `the uses note counts the bars an item takes`() {
        assertEquals("Iron bar x5", uses(smith("item" to "$IRON_PLATEBODY")))
    }

    @Test
    fun `the uses note waits for an item`() {
        assertEquals(Uses.NOTHING_PICKED, uses(smith()))
    }

    @Test
    fun `a smith step on earlier steps needs its bar from a step before it`() {
        assertRejected("no step before gets iron bar") { type.resolve(smith("item" to "$IRON_PLATEBODY", "input" to "earlier"), smelted) }
    }

    @Test
    fun `a smith step on earlier steps smiths the bars they get`() {
        val step = type.resolve(smith("item" to "$BRONZE_DAGGER", "input" to "earlier"), smelted)

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile), step)
    }

    @Test
    fun `a new smith step after a smelt step takes its bars`() {
        assertEquals(smith("input" to "earlier"), type.newSettings(smelted))
    }

    @Test
    fun `a smith step's input follows the flow until one is kept`() {
        assertEquals(InputSource.EARLIER_STEPS, type.input(smith(), smelted))
    }

    @Test
    fun `a smith step's slot names the step its bars come from`() {
        assertEquals(listOf("from step 3", "all of them"), type.details(smith("item" to "$BRONZE_DAGGER", "input" to "earlier"), smelted))
    }

    @Test
    fun `the steps after a smith step know it makes its item`() {
        val after = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, walkedTo).after(FlowContext(walkedTo, setOf(2349)))

        assertEquals(FlowContext(walkedTo, setOf(2349, BRONZE_DAGGER)), after)
    }

    @Test
    fun `a smith step shows the Smithing icon and level`() {
        assertEquals(StepIcon.Skill(Skill.SMITHING), type.icon(smith()))
        assertEquals(Skill.SMITHING, type.skill(smith()))
    }

    @Test
    fun `a smith step's slot says how many, from the bank`() {
        assertEquals(listOf("from the bank", "all of them"), type.details(smith("item" to "$BRONZE_DAGGER"), FlowContext()))
    }

    @Test
    fun `a smith step without an item takes no bar from the steps before`() {
        assertEquals("No bars before it", type.details(smith("input" to "earlier"), smelted).first())
    }

    @Test
    fun `a smith step's target is the item, by id`() {
        val target = type.target(FakeNames(items = mapOf(BRONZE_DAGGER to "Bronze dagger")))

        assertEquals("Bronze dagger", target.picked(smith("item" to "$BRONZE_DAGGER"))?.label)
    }

    private fun uses(settings: StepSettings): String = (type.fields(LunaGameNames)[2] as StepField.Note).text(settings, FlowContext())

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    private companion object {
        const val BRONZE_DAGGER = 1205
        const val IRON_PLATEBODY = 1115
    }

    @Test
    fun `a smith step needs a hammer and the bar`() {
        assertEquals(
            listOf(StepNeeds(tools = listOf(ToolNeed(word = null, mapOf(2347 to 1))), inputs = listOf(2349))),
            SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, walkedTo).needs(),
        )
    }
}
