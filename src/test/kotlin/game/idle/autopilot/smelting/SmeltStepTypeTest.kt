package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmeltStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3226, 3256))

    private fun choices(index: Int) = SmeltStepType.fields[index].choices(listOf("", "", ""))

    /** Luna's bars name their ores from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smelt line names its bar, all of it, within the default radius`() {
        assertEquals(listOf("bronze", "all", "10"), SmeltStepType.parse(listOf("bronze")))
    }

    @Test
    fun `a smelt line may start with a count and end with a radius`() {
        assertEquals(listOf("iron", "5", "20"), SmeltStepType.parse(listOf("5", "iron", "within", "20")))
    }

    @Test
    fun `defaults are left out of the line, anything else is written`() {
        assertEquals("smelt bronze", SmeltStepType.line(listOf("bronze", "all", "10")))
        assertEquals("smelt 1 bronze within 5", SmeltStepType.line(listOf("bronze", "1", "5")))
    }

    @Test
    fun `smelt without a bar`() {
        val error = assertThrows<FlowError> { SmeltStepType.parse(listOf("5")) }

        assertEquals("smelt needs a bar: smelt [<n>] <bar> [within <r>]", error.message)
    }

    @Test
    fun `a smelt step works around the work spot the steps before it set, whatever the case of the bar`() {
        assertEquals(SmeltStep(BarType.STEEL, 15, walkedTo, amount = 5), SmeltStepType.resolve(listOf("Steel", "5", "15"), FlowContext(workSpot = walkedTo)))
    }

    @Test
    fun `a smelt step without an amount smelts all the ore`() {
        assertEquals(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile, amount = null), SmeltStepType.resolve(listOf("bronze", "all", "10"), FlowContext()))
    }

    @Test
    fun `an unknown bar is rejected`() {
        val error = assertThrows<FlowError> { SmeltStepType.resolve(listOf("tin", "all", "10"), FlowContext()) }

        assertEquals("'tin' is not a bar to smelt", error.message)
    }

    @Test
    fun `the builder offers the bars, easiest first, amounts and a few radii`() {
        assertEquals(listOf("bronze", "iron", "silver", "steel", "gold", "mithril", "adamant", "rune"), choices(0))
        assertEquals(listOf("all", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
    }

    @Test
    fun `the steps after a smelt step know it makes its bar`() {
        assertEquals(FlowContext(walkedTo, setOf(436, 2349)), SmeltStep(BarType.BRONZE, 10, walkedTo).after(FlowContext(walkedTo, setOf(436))))
    }
}
