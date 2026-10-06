package game.idle.autopilot.mining

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.mining.Ore
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MineStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3285, 3365))

    private fun choices(index: Int) = MineStepType.fields[index].choices(listOf("", "", ""))

    /** Luna's ores name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a mine line names its ore, until the inventory is full, within the default radius`() {
        assertEquals(listOf("copper", "full", "10"), MineStepType.parse(listOf("copper")))
    }

    @Test
    fun `a mine line may start with a count and end with a radius`() {
        assertEquals(listOf("tin", "5", "15"), MineStepType.parse(listOf("5", "tin", "within", "15")))
    }

    @Test
    fun `defaults are left out of the line, anything else is written`() {
        assertEquals("mine copper", MineStepType.line(listOf("copper", "full", "10")))
        assertEquals("mine 1 tin within 5", MineStepType.line(listOf("tin", "1", "5")))
    }

    @Test
    fun `mine without an ore`() {
        assertRejected("mine needs an ore: mine [<n>] <ore> [within <r>]") { MineStepType.parse(listOf("5")) }
    }

    @Test
    fun `a mine step works around the work spot the steps before it set, whatever the case of the ore`() {
        val step = MineStepType.resolve(listOf("Iron", "5", "15"), FlowContext(workSpot = walkedTo))

        assertEquals(MineStep(Ore.IRON, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a mine step with no walk before it works around the run tile, until the inventory is full`() {
        assertEquals(MineStep(Ore.TIN, 10, WorkSpot.RunTile, amount = null), MineStepType.resolve(listOf("tin", "full", "10"), FlowContext()))
    }

    @Test
    fun `an ore without rocks is rejected`() {
        assertRejected("'rune_essence' is not an ore with rocks to mine") {
            MineStepType.resolve(listOf("rune_essence", "full", "10"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the ores with rocks, easiest first, amounts and a few radii`() {
        assertEquals(listOf("clay", "tin", "copper", "iron", "silver", "coal", "gold", "mithril", "adamant", "rune"), choices(0))
        assertEquals(listOf("full", "1", "5", "10"), choices(1))
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
}
