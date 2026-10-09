package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CookStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3228))
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val logs = 1511

    private fun cook(vararg values: Pair<String, String>) = StepSettings("cook", mapOf(*values))

    /** Luna's food names itself from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a cook step with its defaults reads as cook alone`() {
        assertEquals("cook", CookStepType.summary(cook("within" to "10")))
    }

    @Test
    fun `a cook step's count and radius are written`() {
        assertEquals("cook 1 within 5", CookStepType.summary(cook("amount" to "1", "within" to "5")))
        assertEquals("cook within 5", CookStepType.summary(cook("within" to "5")))
    }

    @Test
    fun `cook cooks the raw food the steps before it gathered, around the work spot`() {
        val step = CookStepType.resolve(cook("amount" to "2", "within" to "15"), FlowContext(walkedTo, gathered = setOf(rawShrimps, rawAnchovies, logs)))

        assertEquals(CookStep(setOf(rawShrimps, rawAnchovies), 15, walkedTo, amount = 2), step)
    }

    @Test
    fun `cook with nothing raw gathered before it is rejected`() {
        val error = assertThrows<FlowError> { CookStepType.resolve(cook(), FlowContext(gathered = setOf(logs))) }

        assertEquals("cook needs raw food from a step before it", error.message)
    }

    @Test
    fun `the configure screen notes where the raw food comes from, types the amount and the radius`() {
        assertEquals(
            listOf("Input (left): note", "Amount (left): typed amount 1..1000, button 'All'", "Within (right): typed within 1..32"),
            described(CookStepType.fields(FakeNames())),
        )
    }

    @Test
    fun `the configure screen's input note names the step that gets the raw food`() {
        val note = CookStepType.fields(FakeNames()).first() as StepField.Note

        assertEquals("Raw food from step 1", note.text(cook(), FlowContext(gatheredBy = mapOf(317 to 1))))
    }

    @Test
    fun `the configure screen shows the Cooking level`() {
        assertEquals(Skill.COOKING, CookStepType.skill(cook()))
    }

    @Test
    fun `the steps after a cook step know what the steps before it knew`() {
        val context = FlowContext(walkedTo, gathered = setOf(rawShrimps))

        assertEquals(context, CookStep(setOf(rawShrimps), 10, walkedTo).after(context))
    }

    @Test
    fun `a cook step shows the Cooking icon`() {
        assertEquals(StepIcon.Skill(Skill.COOKING), CookStepType.icon(StepSettings("cook")))
    }

    @Test
    fun `a cook step's slot names the step its raw food comes from, all of it`() {
        assertEquals(listOf("Raw food from step 1", "all of them"), CookStepType.details(cook(), FlowContext(gatheredBy = mapOf(317 to 1))))
    }

    @Test
    fun `a cook step's slot says how much a lap cooks`() {
        assertEquals("5 per lap", CookStepType.details(cook("amount" to "5"), FlowContext()).last())
    }

    @Test
    fun `a cook step with no raw food before it says so`() {
        assertEquals("No raw food before it", CookStepType.details(cook(), FlowContext()).first())
    }

    @Test
    fun `a cook step has no target yet`() {
        assertNull(CookStepType.target(FakeNames()))
    }
}
