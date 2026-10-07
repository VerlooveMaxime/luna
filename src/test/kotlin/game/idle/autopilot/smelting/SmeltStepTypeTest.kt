package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
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

    private fun smelt(vararg values: Pair<String, String>) = StepSettings("smelt", mapOf(*values))

    private fun choices(index: Int) = (SmeltStepType.fields[index] as StepField.Choice).choices(smelt())

    /** Luna's bars name their ores from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smelt step reads as its bar, its defaults left out, anything else written`() {
        assertEquals("smelt bronze", SmeltStepType.summary(smelt("bar" to "bronze", "within" to "10")))
        assertEquals("smelt 1 bronze within 5", SmeltStepType.summary(smelt("bar" to "bronze", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a smelt step without a bar reads with a question mark and is rejected`() {
        assertEquals("smelt ?", SmeltStepType.summary(smelt()))
        assertEquals("smelt needs a bar", assertThrows<FlowError> { SmeltStepType.resolve(smelt(), FlowContext()) }.message)
    }

    @Test
    fun `a smelt step works around the work spot the steps before it set, whatever the case of the bar`() {
        val step = SmeltStepType.resolve(smelt("bar" to "Steel", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(SmeltStep(BarType.STEEL, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smelt step without an amount smelts all the ore`() {
        assertEquals(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile, amount = null), SmeltStepType.resolve(smelt("bar" to "bronze"), FlowContext()))
    }

    @Test
    fun `an unknown bar is rejected`() {
        val error = assertThrows<FlowError> { SmeltStepType.resolve(smelt("bar" to "tin"), FlowContext()) }

        assertEquals("'tin' is not a bar to smelt", error.message)
    }

    @Test
    fun `the builder offers the bars, easiest first, amounts and a few radii`() {
        assertEquals(listOf("bronze", "iron", "silver", "steel", "gold", "mithril", "adamant", "rune"), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
    }

    @Test
    fun `the steps after a smelt step know it makes its bar`() {
        assertEquals(FlowContext(walkedTo, setOf(436, 2349)), SmeltStep(BarType.BRONZE, 10, walkedTo).after(FlowContext(walkedTo, setOf(436))))
    }
}
