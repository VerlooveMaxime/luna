package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.LunaGameNames
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmeltStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3226, 3256))

    private fun smelt(vararg values: Pair<String, String>) = StepSettings("smelt", mapOf(*values))

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
    fun `the configure screen searches the bar, types the amount and the radius`() {
        assertEquals(
            listOf(
                "Bar (left): search bar, 'Which bar would you like to smelt?'",
                "Amount (left): typed amount 1..1000, button 'All'",
                "Within (right): typed within 1..32",
            ),
            described(SmeltStepType.fields(FakeNames())),
        )
    }

    @Test
    fun `the configure screen shows the Smithing level`() {
        assertEquals(Skill.SMITHING, SmeltStepType.skill(StepSettings()))
    }

    @Test
    fun `the steps after a smelt step know it makes its bar`() {
        assertEquals(FlowContext(walkedTo, setOf(436, 2349)), SmeltStep(BarType.BRONZE, 10, walkedTo).after(FlowContext(walkedTo, setOf(436))))
    }

    @Test
    fun `a smelt step shows the Smithing icon`() {
        assertEquals(StepIcon.Skill(Skill.SMITHING), SmeltStepType.icon(StepSettings("smelt")))
    }

    @Test
    fun `a smelt step's target is its bar among the bars there are`() {
        val target = SmeltStepType.target(LunaGameNames)

        assertEquals(listOf("bar", "Bronze bar"), listOf(target.key, target.picked(smelt("bar" to "bronze"))?.label))
    }

    @Test
    fun `a smelt step's slot says how many bars a lap smelts`() {
        assertEquals(listOf("5 per lap"), SmeltStepType.details(smelt("bar" to "bronze", "amount" to "5", "within" to "15"), FlowContext()))
    }

    @Test
    fun `a smelt step without a count smelts all of them, within the default radius`() {
        assertEquals(listOf("all of them"), SmeltStepType.details(smelt(), FlowContext()))
    }
}
