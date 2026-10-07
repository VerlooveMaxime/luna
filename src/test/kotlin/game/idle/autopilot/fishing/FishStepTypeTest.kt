package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FishStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3228))

    private fun fish(vararg values: Pair<String, String>) = StepSettings("fish", mapOf(*values))

    private fun choices(index: Int) = (FishStepType.fields[index] as StepField.Choice).choices(fish())

    /** Luna's fish name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a fish step reads as its fish, its defaults left out, anything else written`() {
        assertEquals("fish shrimp", FishStepType.summary(fish("fish" to "shrimp", "within" to "10")))
        assertEquals("fish 1 shrimp within 5", FishStepType.summary(fish("fish" to "shrimp", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a fish step without a fish reads with a question mark and is rejected`() {
        assertEquals("fish ?", FishStepType.summary(fish()))
        assertRejected("fish needs a fish") { FishStepType.resolve(fish(), FlowContext()) }
    }

    @Test
    fun `a fish step fishes around the work spot, whatever the case`() {
        val step = FishStepType.resolve(fish("fish" to "Shrimp", "amount" to "1", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(FishStep(FishingMethod.SHRIMP, 15, walkedTo, amount = 1), step)
    }

    @Test
    fun `a fish not caught yet lists the ones that are`() {
        assertRejected("'shark' is not a fish you can catch yet. Fish: shrimp") { FishStepType.resolve(fish("fish" to "shark"), FlowContext()) }
    }

    @Test
    fun `the builder offers the fish, amounts and a few radii`() {
        assertEquals(listOf("shrimp"), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
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
