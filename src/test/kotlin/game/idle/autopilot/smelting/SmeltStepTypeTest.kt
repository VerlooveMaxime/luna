package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.Uses
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
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
    private val copperOre = 436
    private val tinOre = 438
    private val type = SmeltStepType(LunaGameNames)
    private val mined = FlowContext(gathered = setOf(copperOre, tinOre), gatheredBy = mapOf(copperOre to 1, tinOre to 2))

    private fun smelt(vararg values: Pair<String, String>) = StepSettings("smelt", mapOf(*values))

    /** Luna's bars name their ores from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smelt step reads as its bar, its defaults left out, anything else written`() {
        assertEquals("smelt bronze", type.summary(smelt("bar" to "bronze", "within" to "10")))
        assertEquals("smelt 1 bronze within 5", type.summary(smelt("bar" to "bronze", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a smelt step without a bar reads with a question mark and is rejected`() {
        assertEquals("smelt ?", type.summary(smelt()))
        assertEquals("smelt needs a bar", assertThrows<FlowError> { type.resolve(smelt(), FlowContext()) }.message)
    }

    @Test
    fun `a smelt step works around the work spot the steps before it set, whatever the case of the bar`() {
        val step = type.resolve(smelt("bar" to "Steel", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(SmeltStep(BarType.STEEL, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smelt step without an amount smelts all the ore`() {
        assertEquals(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile, amount = null), type.resolve(smelt("bar" to "bronze"), FlowContext()))
    }

    @Test
    fun `an unknown bar is rejected`() {
        val error = assertThrows<FlowError> { type.resolve(smelt("bar" to "tin"), FlowContext()) }

        assertEquals("'tin' is not a bar to smelt", error.message)
    }

    @Test
    fun `the configure screen toggles the input, searches the bar, notes its ores, types the amount and the radius`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Bar (left): search bar, 'Which bar would you like to smelt?'",
                "Uses (left): note",
                "Amount (left): typed amount 1..1000, button 'All'",
                "Within (right): typed within 1..32",
            ),
            described(type.fields(FakeNames())),
        )
    }

    @Test
    fun `the uses note names the bar's ores and their counts`() {
        assertEquals("Iron ore + coal x2", uses(smelt("bar" to "steel")))
    }

    @Test
    fun `the uses note waits for a bar`() {
        assertEquals(Uses.NOTHING_PICKED, uses(smelt()))
    }

    @Test
    fun `a smelt step on earlier steps needs every ore from a step before it`() {
        val error = assertThrows<FlowError> { type.resolve(smelt("bar" to "bronze", "input" to "earlier"), FlowContext(gathered = setOf(copperOre))) }

        assertEquals("no step before gets tin ore", error.message)
    }

    @Test
    fun `a smelt step on earlier steps smelts the ores they get`() {
        assertEquals(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile), type.resolve(smelt("bar" to "bronze", "input" to "earlier"), mined))
    }

    @Test
    fun `a new smelt step after mining steps takes their ores`() {
        assertEquals(smelt("input" to "earlier"), type.newSettings(mined))
    }

    @Test
    fun `a smelt step's input follows the flow until one is kept`() {
        assertEquals(InputSource.BANK, type.input(smelt(), FlowContext()))
    }

    @Test
    fun `a smelt step's slot names the steps its ores come from`() {
        assertEquals(listOf("from steps 1, 2", "all of them"), type.details(smelt("bar" to "bronze", "input" to "earlier"), mined))
    }

    @Test
    fun `the configure screen shows the Smithing level`() {
        assertEquals(Skill.SMITHING, type.skill(StepSettings()))
    }

    @Test
    fun `the steps after a smelt step know it makes its bar`() {
        assertEquals(FlowContext(walkedTo, setOf(436, 2349)), SmeltStep(BarType.BRONZE, 10, walkedTo).after(FlowContext(walkedTo, setOf(436))))
    }

    @Test
    fun `a smelt step shows the Smithing icon`() {
        assertEquals(StepIcon.Skill(Skill.SMITHING), type.icon(StepSettings("smelt")))
    }

    @Test
    fun `a smelt step's target is its bar among the bars there are`() {
        val target = type.target(LunaGameNames)

        assertEquals(listOf("bar", "Bronze bar"), listOf(target.key, target.picked(smelt("bar" to "bronze"))?.label))
    }

    @Test
    fun `a smelt step's slot says how many bars a lap smelts, from the bank`() {
        assertEquals(listOf("from the bank", "5 per lap"), type.details(smelt("bar" to "bronze", "amount" to "5", "within" to "15"), FlowContext()))
    }

    @Test
    fun `a smelt step without a bar smelts all of them`() {
        assertEquals(listOf("from the bank", "all of them"), type.details(smelt(), FlowContext()))
    }

    private fun uses(settings: StepSettings): String =
        (type.fields(LunaGameNames)[2] as StepField.Note).text(settings, FlowContext())
}
