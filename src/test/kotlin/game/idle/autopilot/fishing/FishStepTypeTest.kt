package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
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

        assertEquals(FishStep(FishingMethod(Tool.SMALL_NET), 15, walkedTo, amount = 1), step)
    }

    @Test
    fun `a method is picked by its first fish`() {
        val step = FishStepType.resolve(fish("fish" to "shark"), FlowContext())

        assertEquals(FishingMethod(Tool.SHARK_HARPOON), (step as FishStep).method)
    }

    @Test
    fun `a fish not caught yet lists the ones that are`() {
        assertRejected("'karambwan' is not a fish you can catch yet. Fish: $WORDS") {
            FishStepType.resolve(fish("fish" to "karambwan"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the fish, amounts and a few radii`() {
        assertEquals(WORDS.split(", "), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
    }

    @Test
    fun `every tool Luna's spot table uses is a method, lowest level first, the karambwan vessel not`() {
        val tools = listOf(
            Tool.SMALL_NET, Tool.FISHING_ROD, Tool.BIG_NET, Tool.FLY_FISHING_ROD, Tool.HARPOON, Tool.LOBSTER_POT,
            Tool.MONKFISH_NET, Tool.SHARK_HARPOON,
        )

        assertEquals(tools, FishingMethod.ALL.map { it.tool })
    }

    @Test
    fun `the small net works the sea's and the island's net spots and catches shrimp and anchovies`() {
        val net = FishingMethod(Tool.SMALL_NET)

        assertEquals(setOf(316, 319, 320, 323, 325, 326, 327, 330, 1331, 952), net.spotIds)
        assertEquals(emptySet<Int>(), net.secondClickSpots)
        assertEquals(setOf(317, 321), net.catchIds)
    }

    @Test
    fun `the rod baits the contest spots on their first option and the sea and river spots on their second`() {
        val rod = FishingMethod(Tool.FISHING_ROD)

        assertEquals(setOf(233, 234, 235, 236), rod.spotIds - rod.secondClickSpots)
        assertEquals(FishingSpot.SEA.ids + FishingSpot.RIVER.ids, rod.secondClickSpots)
    }

    @Test
    fun `a method with no spot in Luna's table works nowhere`() {
        assertEquals(emptySet<Int>(), FishingMethod(Tool.KARAMBWAN_VESSEL).spotIds)
    }

    @Test
    fun `the steps after a fish step know it gathers its catches`() {
        val after = FishStep(FishingMethod(Tool.SMALL_NET), 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(1511)))

        assertEquals(FlowContext(walkedTo, setOf(1511, 317, 321)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    private companion object {
        const val WORDS = "shrimp, sardine, mackerel, trout, tuna, lobster, monkfish, shark"
    }
}
