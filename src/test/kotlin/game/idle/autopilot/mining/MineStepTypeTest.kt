package game.idle.autopilot.mining

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.location.Tile
import game.skill.mining.Ore
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MineStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3285, 3365))

    private fun mine(vararg values: Pair<String, String>) = StepSettings("mine", mapOf(*values))

    /** Luna's ores name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a mine step reads as its ore, its defaults left out, anything else written`() {
        assertEquals("mine copper", MineStepType.summary(mine("ore" to "copper", "within" to "10")))
        assertEquals("mine 1 tin within 5", MineStepType.summary(mine("ore" to "tin", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a mine step without an ore reads with a question mark and is rejected`() {
        assertEquals("mine ?", MineStepType.summary(mine()))
        assertRejected("mine needs an ore") { MineStepType.resolve(mine(), FlowContext()) }
    }

    @Test
    fun `a mine step works around the work spot the steps before it set, whatever the case of the ore`() {
        val step = MineStepType.resolve(mine("ore" to "Iron", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(MineStep(Ore.IRON, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a mine step with no walk before it works around the run tile, until the inventory is full`() {
        assertEquals(MineStep(Ore.TIN, 10, WorkSpot.RunTile, amount = null), MineStepType.resolve(mine("ore" to "tin"), FlowContext()))
    }

    @Test
    fun `an ore without rocks is rejected`() {
        assertRejected("'rune_essence' is not an ore with rocks to mine") {
            MineStepType.resolve(mine("ore" to "rune_essence"), FlowContext())
        }
    }

    @Test
    fun `the configure screen searches the rock, types the amount and the radius`() {
        assertEquals(
            listOf(
                "Rock (left): search ore, 'Which rock would you like to mine?'",
                "Amount (left): typed amount 1..1000, button 'Full'",
                "Within (right): typed within 1..32",
            ),
            described(MineStepType.fields(FakeNames())),
        )
    }

    @Test
    fun `the configure screen shows the Mining level`() {
        assertEquals(Skill.MINING, MineStepType.skill(StepSettings()))
    }

    @Test
    fun `the steps after a mine step know it gathers its ore`() {
        val after = MineStep(Ore.COPPER, 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(438)))

        assertEquals(FlowContext(walkedTo, setOf(438, 436)), after)
    }

    @Test
    fun `a mine step shows a value that is no option as it is kept, without a picture`() {
        assertEquals(StepPick("nothing", null), MineStepType.pick(mine("ore" to "nothing"), FakeNames()))
    }

    @Test
    fun `a new mine step starts with no settings`() {
        assertEquals(StepSettings("mine"), MineStepType.newSettings(FlowContext()))
    }

    @Test
    fun `a mine step takes nothing in, so its searches count the bank`() {
        assertEquals(InputSource.BANK, MineStepType.input(mine(), FlowContext()))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<FlowError> { action() }
        assertEquals(message, error.message)
    }

    @Test
    fun `a mine step shows the Mining icon`() {
        assertEquals(StepIcon.Skill(Skill.MINING), MineStepType.icon(StepSettings("mine")))
    }

    @Test
    fun `a mine step's target is its ore among the ores there are`() {
        val target = MineStepType.target(LunaGameNames)

        assertEquals(listOf("ore", "Copper ore"), listOf(target.key, target.picked(mine("ore" to "copper"))?.label))
    }

    @Test
    fun `a mine step's slot says how many ores a lap mines`() {
        assertEquals(listOf("5 per lap"), MineStepType.details(mine("ore" to "copper", "amount" to "5", "within" to "15"), FlowContext()))
    }

    @Test
    fun `a mine step without a count mines until the bag is full, within the default radius`() {
        assertEquals(listOf("until the bag is full"), MineStepType.details(mine(), FlowContext()))
    }
}
