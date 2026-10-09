package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
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

        assertEquals("cook comes after a fish step, so the flow knows what to cook", error.message)
    }

    @Test
    fun `the builder offers all or a few counts, and a few radii`() {
        assertEquals(listOf("", "1", "5", "10"), (CookStepType.fields[0] as StepField.Choice).choices(cook()))
        assertEquals(listOf("5", "10", "15", "20", "30"), (CookStepType.fields[1] as StepField.Choice).choices(cook()))
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
}
