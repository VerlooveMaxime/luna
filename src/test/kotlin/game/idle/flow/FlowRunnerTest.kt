package game.idle.flow

import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import game.skill.woodcutting.cutTree.Tree
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
    private var finished = 0

    private fun runner(vararg lines: String, startAt: Int = 0) =
        FlowRunner(resolver.resolve(lines.toList()), startAt, player, activities) { finished++ }

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
    fun `until inventory full ends the chop step`() {
        val runner = runner("chop normal @varrock_west until inventory full", "bank deposit all")
        runner.act()
        player.full = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop varrock_west:1", "bank varrock_west:1"), activities.log)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `until n logs counts the chopped kinds in inventory and bank`() {
        val runner = runner("chop normal @varrock_west until 10 logs", "bank deposit all")
        player.owned[Tree.NORMAL.logId] = 9
        runner.act()
        player.owned[Tree.NORMAL.logId] = 10

        runner.act()

        assertEquals(listOf("chop varrock_west:1"), activities.log)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `until level ends the chop step at that level`() {
        val runner = runner("chop normal @varrock_west until level 5", "bank deposit all")
        player.level = 4
        runner.act()
        player.level = 5

        runner.act()

        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `a step that says it is done is over`() {
        val runner = runner("chop normal @varrock_west", "bank deposit all", "loop")
        runner.act()
        runner.act()
        activities.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop varrock_west:1", "chop varrock_west:2", "bank varrock_west:1"), activities.log)
    }

    @Test
    fun `loop goes back to the first step with a fresh activity`() {
        val runner = runner("chop normal @varrock_west", "bank deposit all", "loop", startAt = 1)
        runner.act()
        activities.started[0].done = true

        runner.act()
        runner.act()
        runner.act()

        assertEquals(listOf("bank varrock_west:1", "chop varrock_west:1"), activities.log)
        assertEquals(listOf(2, 0), player.savedSteps)
    }

    @Test
    fun `running off the end finishes the flow once`() {
        val runner = runner("chop normal @varrock_west until inventory full")
        player.full = true

        runner.act()
        runner.act()

        assertEquals(1, finished)
        assertEquals(listOf("Autopilot: flow finished."), player.told)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `a saved step past the end finishes at once`() {
        runner("chop normal @varrock_west", startAt = 7).act()

        assertEquals(1, finished)
        assertEquals(emptyList<String>(), activities.log)
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
}
