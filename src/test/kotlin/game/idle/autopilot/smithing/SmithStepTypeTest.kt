package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.LevelNeed
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.Uses
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.location.Area
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

    private val picked = Tile(3101, 3509)
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
    fun `a smith step shows an item no table row smiths as it is kept, without a picture`() {
        assertEquals(StepPick("dagger", null), type.pick(smith("item" to "dagger"), FakeNames()))
    }

    @Test
    fun `a smith step without an item is rejected`() {
        assertRejected("Needs an item") { type.resolve(smith(), FlowContext()) }
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
    fun `the configure screen toggles the input, searches the item, notes its bars, types the amount, picks the work spot and the radius`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Item (left): search item, 'What would you like to smith?'",
                "Uses (left): note",
                "Amount (left): typed amount 1..2147483647, button 'All'",
                "Work spot (right): map spot, none 'From the flow'",
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
        assertRejected("No step before gets iron bar") { type.resolve(smith("item" to "$IRON_PLATEBODY", "input" to "earlier"), smelted) }
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
        assertEquals("from no step", type.details(smith("input" to "earlier"), smelted).first())
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
    fun `a smith step needs a hammer, the bar and the item's Smithing level`() {
        assertEquals(
            listOf(StepNeeds(tools = listOf(SmithStepType.HAMMER), inputs = listOf(2351), level = LevelNeed(Skill.SMITHING, 15))),
            SmithStep(BarType.IRON, SmithingTable.DAGGER, 10, walkedTo).needs(),
        )
    }

    @Test
    fun `a smith step with a picked work spot works around it, not where the flow went`() {
        val step = type.resolve(smith("item" to "$BRONZE_DAGGER", "input" to "bank", "spot" to "3101 3509"), FlowContext(workSpot = walkedTo))

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.Own(picked)), step)
    }

    @Test
    fun `the steps after a smith step with a picked work spot work there`() {
        val after = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 12, WorkSpot.Own(picked)).after(FlowContext())

        assertEquals(WorkSpot.At(picked), after.workSpot)
    }

    @Test
    fun `a smith step walks to its picked spot from outside its area, and to no spot when it works where the flow went`() {
        val own = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 12, WorkSpot.Own(picked))
        val flows = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 12, WorkSpot.At(picked))

        assertEquals(listOf(Area(picked, 12), null), listOf(own.ownArea, flows.ownArea))
    }
}
