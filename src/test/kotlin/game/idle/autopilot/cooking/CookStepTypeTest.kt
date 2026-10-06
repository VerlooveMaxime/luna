package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CookStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3228))
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val logs = 1511

    /** Luna's food names itself from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `cook alone cooks everything within the default radius`() {
        assertEquals(listOf("all", "10"), CookStepType.parse(emptyList()))
        assertEquals("cook", CookStepType.line(listOf("all", "10")))
    }

    @Test
    fun `cook may take a count and a radius`() {
        assertEquals(listOf("1", "5"), CookStepType.parse(listOf("1", "within", "5")))
        assertEquals("cook 1 within 5", CookStepType.line(listOf("1", "5")))
        assertEquals("cook within 5", CookStepType.line(listOf("all", "5")))
    }

    @Test
    fun `anything else is rejected`() {
        val error = assertThrows<FlowError> { CookStepType.parse(listOf("shrimp")) }

        assertEquals("Unexpected 'shrimp' after the count: cook [<n>] [within <r>]", error.message)
    }

    @Test
    fun `cook cooks the raw food the steps before it gathered, around the work spot`() {
        val step = CookStepType.resolve(listOf("2", "15"), FlowContext(walkedTo, gathered = setOf(rawShrimps, rawAnchovies, logs)))

        assertEquals(CookStep(setOf(rawShrimps, rawAnchovies), 15, walkedTo, amount = 2), step)
    }

    @Test
    fun `cook with nothing raw gathered before it is rejected`() {
        val error = assertThrows<FlowError> { CookStepType.resolve(listOf("all", "10"), FlowContext(gathered = setOf(logs))) }

        assertEquals("cook comes after a fish step, so the flow knows what to cook", error.message)
    }

    @Test
    fun `the builder offers all or a few counts, and a few radii`() {
        assertEquals(listOf("all", "1", "5", "10"), CookStepType.fields[0].choices(emptyList()))
        assertEquals(listOf("5", "10", "15", "20", "30"), CookStepType.fields[1].choices(emptyList()))
    }

    @Test
    fun `the steps after a cook step know what the steps before it knew`() {
        val context = FlowContext(walkedTo, gathered = setOf(rawShrimps))

        assertEquals(context, CookStep(setOf(rawShrimps), 10, walkedTo).after(context))
    }
}
