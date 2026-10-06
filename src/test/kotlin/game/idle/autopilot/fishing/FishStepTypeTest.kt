package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FishStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3228))

    /** Luna's fish name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a fish line names its fish, until the inventory is full, within the default radius`() {
        assertEquals(listOf("shrimp", "full", "10"), FishStepType.parse(listOf("shrimp")))
        assertEquals("fish shrimp", FishStepType.line(listOf("shrimp", "full", "10")))
    }

    @Test
    fun `a fish line may start with a count and end with a radius`() {
        assertEquals(listOf("shrimp", "1", "5"), FishStepType.parse(listOf("1", "shrimp", "within", "5")))
        assertEquals("fish 1 shrimp within 5", FishStepType.line(listOf("shrimp", "1", "5")))
    }

    @Test
    fun `fish without a fish`() {
        assertRejected("fish needs a fish: fish [<n>] <fish> [within <r>]") { FishStepType.parse(listOf("2")) }
    }

    @Test
    fun `anything else after the fish is rejected`() {
        assertRejected("Unexpected 'now' after the fish: fish [<n>] <fish> [within <r>]") { FishStepType.parse(listOf("shrimp", "now")) }
    }

    @Test
    fun `a fish step fishes around the work spot, whatever the case`() {
        val step = FishStepType.resolve(listOf("Shrimp", "1", "15"), FlowContext(workSpot = walkedTo))

        assertEquals(FishStep(FishingMethod.SHRIMP, 15, walkedTo, amount = 1), step)
    }

    @Test
    fun `a fish not caught yet lists the ones that are`() {
        assertRejected("'shark' is not a fish you can catch yet. Fish: shrimp") { FishStepType.resolve(listOf("shark", "full", "10"), FlowContext()) }
    }

    @Test
    fun `the builder offers the fish, amounts and a few radii`() {
        assertEquals(listOf("shrimp"), FishStepType.fields[0].choices(emptyList()))
        assertEquals(listOf("full", "1", "5", "10"), FishStepType.fields[1].choices(emptyList()))
    }

    @Test
    fun `shrimp are netted at the small-net spots, the island's too, and catch shrimp and anchovies`() {
        assertEquals(303, FishingMethod.SHRIMP.tool.id)
        assertEquals(setOf(316, 319, 320, 327, 330, 952), FishingMethod.SHRIMP.spotIds)
        assertEquals(setOf(317, 321), FishingMethod.SHRIMP.catchIds)
    }

    @Test
    fun `the steps after a fish step know it gathers its catches`() {
        val after = FishStep(FishingMethod.SHRIMP, 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(1511)))

        assertEquals(FlowContext(walkedTo, setOf(1511, 317, 321)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
