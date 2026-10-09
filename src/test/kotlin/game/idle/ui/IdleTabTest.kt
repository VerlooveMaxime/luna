package game.idle.ui

import game.idle.IdleState
import game.idle.SavedFlow
import game.idle.flow.StepSettings
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdleTabTest {

    private val tab = idleTab(slots = 2)

    private val chop = StepSettings("chop")
    private val drop = StepSettings("drop")
    private val light = StepSettings("light")
    private val flow = listOf(chop, drop, light)

    private fun saved(slot: Int, name: String, steps: List<StepSettings> = flow) = SavedFlow(slot, name, steps)

    /** [steps] as the current flow, last loaded from or saved to [savedSlot], with [savedFlows] kept. */
    private fun state(steps: List<StepSettings> = flow, savedSlot: Int? = null, vararg savedFlows: SavedFlow) =
        IdleState(steps = steps, savedFlows = savedFlows.toList(), savedSlot = savedSlot)

    @Test
    fun `a stopped autopilot says so`() {
        assertEquals("Autopilot: stopped", tab.status(state())[0])
    }

    @Test
    fun `a running autopilot names its step`() {
        assertEquals("Autopilot: running step 2 of 3", tab.status(state().copy(running = true, stepIndex = 1))[0])
    }

    @Test
    fun `a flow never saved says so with its step count`() {
        assertEquals(listOf("Flow: not saved, 3 steps", ""), tab.status(state()).drop(1))
    }

    @Test
    fun `a flow of one step counts it in the singular`() {
        assertEquals("Flow: not saved, 1 step", tab.status(state(listOf(chop)))[1])
    }

    @Test
    fun `an empty flow of no slot is empty`() {
        assertEquals("Flow: empty", tab.status(state(emptyList()))[1])
    }

    @Test
    fun `a saved flow cleared in the builder is changed, with no steps`() {
        assertEquals("Flow: Willows (changed), 0 steps", tab.status(state(emptyList(), savedSlot = 0, saved(0, "Willows")))[1])
    }

    @Test
    fun `a saved flow goes by its name`() {
        assertEquals("Flow: Willows, 3 steps", tab.status(state(savedSlot = 0, savedFlows = arrayOf(saved(0, "Willows"))))[1])
    }

    @Test
    fun `the current flow goes by its own slot's name`() {
        val current = state(savedSlot = 1, savedFlows = arrayOf(saved(0, "Cows"), saved(1, "Willows")))

        assertEquals("Flow: Willows, 3 steps", tab.status(current)[1])
    }

    @Test
    fun `a flow edited since it was saved is changed`() {
        val edited = state(listOf(chop, drop), savedSlot = 0, saved(0, "Willows"))

        assertEquals("Flow: Willows (changed), 2 steps", tab.status(edited)[1])
    }

    @Test
    fun `a flow line too wide goes on at a space on the third line`() {
        val edited = state(listOf(chop), savedSlot = 1, saved(1, "Mine and smith daggers"))

        assertEquals(listOf("Flow: Mine and smith daggers", "(changed), 1 step"), tab.status(edited).drop(1))
    }

    @Test
    fun `an empty slot says empty, in grey, without a count`() {
        assertEquals(SavedFlowRow("@gry@empty", count = "", current = false, filled = false), tab.rows(state())[1])
    }

    @Test
    fun `a saved flow shows its name and step count`() {
        val row = tab.rows(state(savedFlows = arrayOf(saved(0, "Cows", listOf(chop, drop)))))[0]

        assertEquals(SavedFlowRow("Cows", count = "2 steps", current = false, filled = true), row)
    }

    @Test
    fun `the flow loaded from a slot is its current one`() {
        assertTrue(tab.rows(state(savedSlot = 0, savedFlows = arrayOf(saved(0, "Willows")))).first().current)
    }

    @Test
    fun `the current flow edited since adds a yellow changed to its name`() {
        val edited = state(listOf(chop), savedSlot = 0, saved(0, "Willows"))

        assertEquals("Willows @yel@(changed)", tab.rows(edited)[0].name)
    }

    @Test
    fun `another slot's flow is never changed`() {
        val other = state(listOf(chop), savedSlot = 0, saved(0, "Willows"), saved(1, "Cows"))

        assertEquals("Cows", tab.rows(other)[1].name)
    }

    @Test
    fun `a name too wide for its row is cut`() {
        val row = tab.rows(state(savedFlows = arrayOf(saved(0, "x".repeat(40)))))[0]

        assertEquals("x".repeat(31) + "..", row.name)
    }

    @Test
    fun `a changed name is cut before it, so changed stays whole`() {
        val edited = state(listOf(chop), savedSlot = 0, saved(0, "x".repeat(40)))

        assertEquals("x".repeat(21) + ".. @yel@(changed)", tab.rows(edited)[0].name)
    }

    @Test
    fun `a list that scrolls leaves its names less room`() {
        val row = idleTab(slots = 4).rows(state(savedFlows = arrayOf(saved(0, "x".repeat(40)))))[0]

        assertEquals("x".repeat(28) + "..", row.name)
    }

    @Test
    fun `there is a row per saved-flow slot`() {
        assertEquals(4, idleTab(slots = 4).rows(state()).size)
    }


    @Test
    fun `the updates fill the status lines`() {
        val updates = tab.updates(state())

        assertTrue(updates.containsAll(listOf(
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_1, "Autopilot: stopped"),
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_2, "Flow: not saved, 3 steps"),
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_3, ""),
        )))
    }

    @Test
    fun `a current row shows its frame, Load and x, not New`() {
        val updates = tab.updates(state(savedSlot = 1, savedFlows = arrayOf(saved(1, "Willows"))))

        assertTrue(updates.containsAll(listOf(
            WidgetUpdate.Text(FlowWidgets.rowName(1), "Willows"),
            WidgetUpdate.Text(FlowWidgets.rowCount(1), "3 steps"),
            WidgetUpdate.Visible(FlowWidgets.rowFrameLayer(1), visible = true),
            WidgetUpdate.Visible(FlowWidgets.rowLoadLayer(1), visible = true),
            WidgetUpdate.Visible(FlowWidgets.rowNewLayer(1), visible = false),
            WidgetUpdate.Visible(FlowWidgets.rowDeleteLayer(1), visible = true),
        )))
    }

    @Test
    fun `an empty row shows only New`() {
        val updates = tab.updates(state())

        assertTrue(updates.containsAll(listOf(
            WidgetUpdate.Visible(FlowWidgets.rowFrameLayer(0), visible = false),
            WidgetUpdate.Visible(FlowWidgets.rowLoadLayer(0), visible = false),
            WidgetUpdate.Visible(FlowWidgets.rowNewLayer(0), visible = true),
            WidgetUpdate.Visible(FlowWidgets.rowDeleteLayer(0), visible = false),
        )))
    }

    @Test
    fun `a new flow's empty slot is framed as the current one`() {
        assertEquals(SavedFlowRow("@gry@empty", count = "", current = true, filled = false), tab.rows(state(listOf(chop), savedSlot = 0))[0])
    }

    @Test
    fun `a new flow goes by new flow, with its steps`() {
        assertEquals("Flow: new flow, 1 step", tab.status(state(listOf(chop), savedSlot = 0))[1])
    }

    @Test
    fun `a new flow without steps yet still goes by new flow`() {
        assertEquals("Flow: new flow, 0 steps", tab.status(state(emptyList(), savedSlot = 0))[1])
    }

    @Test
    fun `the tab from the cache measures in the client's plain font`() {
        val cached = IdleTab.fromCache(TestWorld.context.cache, savedFlowSlots = 2)
        val edited = state(listOf(chop, drop, light, chop), savedSlot = 0, saved(0, "Willows"))

        // 177 px in the plain font, past the tab's 170 (measured 2026-10-10).
        assertEquals("4 steps", cached.status(edited)[2])
    }

    @Test
    fun `the tab from the cache has the slots it was given`() {
        assertEquals(3, IdleTab.fromCache(TestWorld.context.cache, savedFlowSlots = 3).slots)
    }
}
