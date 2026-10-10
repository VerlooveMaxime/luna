package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionIcon
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CookStepTypeTest {

    /** Luna's food names itself from the item definitions, which need the cache, before the type reads it. */
    private val world = TestWorld.world

    private val walkedTo = WorkSpot.At(Tile(3086, 3228))
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val logs = 1511
    private val type = CookStepType(FakeNames(items = mapOf(rawShrimps to "Raw shrimps", rawAnchovies to "Raw anchovies")))
    private val fished = FlowContext(walkedTo, gathered = setOf(rawShrimps, rawAnchovies, logs), gatheredBy = mapOf(rawShrimps to 1, rawAnchovies to 1, logs to 2))

    private fun cook(vararg values: Pair<String, String>) = StepSettings("cook", mapOf(*values))

    @Test
    fun `a cook step with its defaults reads as cook alone`() {
        assertEquals("cook", type.summary(cook("within" to "10")))
    }

    @Test
    fun `a cook step's count, food and radius are written`() {
        assertEquals("cook 1 raw shrimps within 5", type.summary(cook("amount" to "1", "raw" to "$rawShrimps", "within" to "5")))
    }

    @Test
    fun `cook cooks the raw food picked, around the work spot`() {
        val step = type.resolve(cook("amount" to "2", "within" to "15", "input" to "earlier", "raw" to "$rawShrimps,$rawAnchovies"), fished)

        assertEquals(CookStep(setOf(rawShrimps, rawAnchovies), 15, walkedTo, amount = 2), step)
    }

    @Test
    fun `cook with no raw food picked cannot work`() {
        val error = assertThrows<FlowError> { type.resolve(cook("input" to "bank"), FlowContext()) }

        assertEquals("cook needs raw food picked", error.message)
    }

    @Test
    fun `the configure screen toggles the input, types the amount, lists the food and types the radius`() {
        assertEquals(
            listOf(
                "Input (left): toggle input earlier 'Earlier steps' / bank 'The bank'",
                "Amount (left): typed amount 1..2147483647, button 'All'",
                "Raw food (left): list raw on 4 rows, 'What would you like to cook?'",
                "Within (right): typed within 1..32",
            ),
            described(type.fields(FakeNames())),
        )
    }

    @Test
    fun `a new cook step after a fishing step cooks every fish it catches`() {
        assertEquals(cook("input" to "earlier", "raw" to "$rawShrimps,$rawAnchovies"), type.newSettings(fished))
    }

    @Test
    fun `a cook step's input follows the flow until one is kept`() {
        assertEquals(InputSource.BANK, type.input(cook(), FlowContext()))
    }

    @Test
    fun `a cook step shows the food it cooks`() {
        assertEquals(StepPick("Raw shrimps", OptionIcon.Item(rawShrimps)), type.pick(cook("raw" to "$rawShrimps"), FakeNames()))
    }

    @Test
    fun `the configure screen shows the Cooking level`() {
        assertEquals(Skill.COOKING, type.skill(cook()))
    }

    @Test
    fun `the steps after a cook step know it makes cooked and burnt food`() {
        val context = FlowContext(walkedTo, gathered = setOf(rawShrimps))

        assertEquals(context.copy(gathered = setOf(rawShrimps, 315, 323)), CookStep(setOf(rawShrimps), 10, walkedTo).after(context))
    }

    @Test
    fun `a cook step needs its raw food in the bag and no tool`() {
        assertEquals(listOf(StepNeeds(inputs = listOf(rawShrimps))), CookStep(setOf(rawShrimps), 10, walkedTo).needs())
    }

    @Test
    fun `a cook step shows the Cooking icon`() {
        assertEquals(StepIcon.Skill(Skill.COOKING), type.icon(StepSettings("cook")))
    }

    @Test
    fun `a cook step's slot names the step its raw food comes from, all of it`() {
        assertEquals(listOf("from step 1", "all of them"), type.details(cook("input" to "earlier", "raw" to "$rawShrimps"), fished))
    }

    @Test
    fun `a cook step has no target to search`() {
        assertNull(type.target(FakeNames()))
    }
}
