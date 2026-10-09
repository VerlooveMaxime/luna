package game.idle.autopilot.mining

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
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

    private fun choices(index: Int) = (MineStepType.fields[index] as StepField.Choice).choices(mine())

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
    fun `the builder offers the ores with rocks, easiest first, amounts and a few radii`() {
        assertEquals(listOf("clay", "tin", "copper", "iron", "silver", "coal", "gold", "mithril", "adamant", "rune"), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
    }

    @Test
    fun `the steps after a mine step know it gathers its ore`() {
        val after = MineStep(Ore.COPPER, 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(438)))

        assertEquals(FlowContext(walkedTo, setOf(438, 436)), after)
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
