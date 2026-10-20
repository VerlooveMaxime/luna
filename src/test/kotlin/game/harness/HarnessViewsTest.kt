package game.harness

import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The JSON keys agents read are the snake_case of the view properties: a renamed property is an API change. */
class HarnessViewsTest {

    private val here = PositionView(3182, 3440, 0)
    private val chop = OptionView(1, "Chop down")

    private fun keysOf(view: Any): Set<String> = GsonUtils.GSON.toJsonTree(view).asJsonObject.keySet()

    @Test
    fun `a player summary`() {
        val view = PlayerSummary(name = "test", index = 1, bot = false, harness = false, position = here)

        assertEquals(setOf("name", "index", "bot", "harness", "position"), keysOf(view))
    }

    @Test
    fun `a skill`() {
        val view = SkillView(id = 8, name = "Woodcutting", level = 10, staticLevel = 10, xp = 1154.0)

        assertEquals(setOf("id", "name", "level", "static_level", "xp"), keysOf(view))
    }

    @Test
    fun `an item`() {
        val view = ItemView(slot = 0, id = 1351, name = "Bronze axe", amount = 1, options = listOf(chop))

        assertEquals(setOf("slot", "id", "name", "amount", "options"), keysOf(view))
    }

    @Test
    fun `an object`() {
        val view = ObjectView(id = 1278, name = "Tree", position = here, distance = 3, type = "INTERACTABLE", options = listOf(chop))

        assertEquals(setOf("id", "name", "position", "distance", "type", "options"), keysOf(view))
    }

    @Test
    fun `an npc`() {
        val view = NpcView(index = 5, id = 1, name = "Man", position = here, distance = 2, combatLevel = 2, options = listOf(chop))

        assertEquals(setOf("index", "id", "name", "position", "distance", "combat_level", "options"), keysOf(view))
    }

    @Test
    fun `a ground item`() {
        val view = GroundItemView(id = 1511, name = "Logs", amount = 1, position = here, distance = 0, options = listOf(chop))

        assertEquals(setOf("id", "name", "amount", "position", "distance", "options"), keysOf(view))
    }

    @Test
    fun `a flow`() {
        val step = FlowStepView(id = 1, kind = "chop", values = mapOf("tree" to "oak"), reflexes = listOf(2), lines = listOf("Oak tree", "5 per lap"), problem = null, goal = null, met = false, counted = 0, leftOut = emptyList())
        val view = FlowView(player = "test", running = true, stepIndex = 0, laps = 2, blocked = "Needs an axe you can use", waiting = "No tree in reach", underWay = "Opening the door", stop = FlowStopView("laps", "5"), travel = FlowTravelView(coins = true, runes = false, charges = false, bank = false), steps = listOf(step), reflexes = emptyList())

        assertEquals(setOf("player", "running", "step_index", "laps", "blocked", "waiting", "under_way", "stop", "travel", "steps", "reflexes"), keysOf(view))
    }

    @Test
    fun `a flow's step`() {
        val view = FlowStepView(id = 1, kind = "chop", values = mapOf("tree" to "oak"), reflexes = listOf(2), lines = listOf("Oak tree", "5 per lap"), problem = "Needs a tree", goal = "until Strength 20", met = true, counted = 3, leftOut = listOf(1521))

        assertEquals(setOf("id", "kind", "values", "reflexes", "lines", "problem", "goal", "met", "counted", "left_out"), keysOf(view))
    }

    @Test
    fun `a flow's stop`() {
        assertEquals(setOf("kind", "after"), keysOf(FlowStopView("laps", "5")))
    }

    @Test
    fun `what a flow's trips may spend`() {
        assertEquals(setOf("coins", "runes", "charges", "bank"), keysOf(FlowTravelView(coins = true, runes = false, charges = false, bank = false)))
    }

    @Test
    fun `a nearby player`() {
        val view = NearbyPlayerView(name = "zezima", index = 2, bot = true, position = here, distance = 4)

        assertEquals(setOf("name", "index", "bot", "position", "distance"), keysOf(view))
    }
}
