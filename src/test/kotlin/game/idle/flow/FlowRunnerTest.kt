package game.idle.flow

import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowRunnerTest {

    private val resolver = FlowResolver(
        LocationCatalog.parse(catalogJson(locationJson("bank" to mapOf("x" to 3186, "y" to 3440)))),
    )
    private val player = FakeFlowPlayer()
    private val activities = FakeStepActivities()

    private fun runner(vararg lines: String, startAt: Int = 0) =
        FlowRunner(resolver.resolve(lines.toList()), startAt, player, activities)

    @Test
    fun `the first act starts the first step and acts on it`() {
        runner("chop normal @varrock_west").act()

        assertEquals(listOf("chop varrock_west:1"), activities.log)
    }

    @Test
    fun `a step keeps acting until it is over`() {
        val runner = runner("chop normal @varrock_west")

        repeat(3) { runner.act() }

        assertEquals(listOf("chop varrock_west:1", "chop varrock_west:2", "chop varrock_west:3"), activities.log)
    }

    @Test
    fun `busy follows the current step`() {
        val runner = runner("chop normal @varrock_west")
        assertFalse(runner.isBusy())
        runner.act()

        activities.started.single().busy = true

        assertTrue(runner.isBusy())
    }

    @Test
    fun `a step that says it is done hands over to the next`() {
        val runner = runner("chop normal @varrock_west", "bank deposit all")
        runner.act()
        runner.act()
        activities.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop varrock_west:1", "chop varrock_west:2", "bank varrock_west:1"), activities.log)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `a drop step runs its own activity`() {
        runner("chop normal @varrock_west", "drop", startAt = 1).act()

        assertEquals(listOf("drop 1:1"), activities.log)
    }

    @Test
    fun `after the last step the flow starts over with a fresh activity`() {
        val runner = runner("chop normal @varrock_west", "bank deposit all", startAt = 1)
        runner.act()
        activities.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("bank varrock_west:1", "chop varrock_west:1"), activities.log)
        assertEquals(listOf(0), player.savedSteps)
    }

    @Test
    fun `a one step flow repeats itself`() {
        val runner = runner("chop normal @varrock_west")
        runner.act()
        activities.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop varrock_west:1", "chop varrock_west:1"), activities.log)
        assertEquals(2, activities.started.size)
    }

    @Test
    fun `a saved step past the end starts at the last step`() {
        runner("chop normal @varrock_west", "bank deposit all", startAt = 7).act()

        assertEquals(listOf("bank varrock_west:1"), activities.log)
    }

    @Test
    fun `a negative saved step starts at the first`() {
        runner("chop normal @varrock_west", startAt = -3).act()

        assertEquals(listOf("chop varrock_west:1"), activities.log)
    }

    @Test
    fun `a flow starting at a saved step resumes there`() {
        runner("chop normal @varrock_west", "bank deposit all", startAt = 1).act()

        assertEquals(listOf("bank varrock_west:1"), activities.log)
    }

    @Test
    fun `an empty flow does nothing`() {
        val runner = runner()

        runner.act()

        assertFalse(runner.isBusy())
        assertEquals(emptyList<String>(), activities.log)
    }
}
