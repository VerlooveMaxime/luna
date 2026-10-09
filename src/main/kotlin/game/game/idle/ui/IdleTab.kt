package game.idle.ui

import game.idle.IdleState
import game.idle.flow.SavedFlows
import io.luna.game.cache.Cache

/**
 * A saved-flow slot of the Idle tab as the player sees it: the flow's [name] (with "(changed)" on the current one when
 * edited since), its step [count], whether the current flow belongs to it, framed (a new flow's empty slot too), and
 * whether the slot is [filled], which shows its Load and x, else its New.
 */
data class SavedFlowRow(val name: String, val count: String, val current: Boolean, val filled: Boolean)

/**
 * What the Idle tab shows for a player's [IdleState] (flow builder v2, S06c, Maxime's answers of 2026-10-10): whether
 * the autopilot runs, the flow and the slot it belongs to, then a row per saved-flow slot of [savedFlows], the way into
 * the builder. The status lines are fitted to the client's [plain] font, the rows to its [small] one.
 */
class IdleTab(private val savedFlows: SavedFlows, private val plain: ClientFont, private val small: ClientFont) {

    val slots: Int get() = savedFlows.slots

    /** Three lines: the autopilot, then the flow, broken onto the third at a space when too wide. */
    fun status(state: IdleState): List<String> {
        val autopilot = if (state.running) "Autopilot: running step ${state.stepIndex + 1} of ${state.steps.size}" else "Autopilot: stopped"
        val flow = plain.wrap(flow(state), FlowWidgets.STATUS_ROOM, FLOW_LINES)
        return listOf(autopilot) + flow + List(FLOW_LINES - flow.size) { "" }
    }

    fun rows(state: IdleState): List<SavedFlowRow> =
        (0 until slots).map { slot ->
            val saved = state.savedFlows.firstOrNull { it.slot == slot }
            val current = state.savedSlot == slot
            if (saved == null) {
                SavedFlowRow(EMPTY, count = "", current, filled = false)
            } else {
                SavedFlowRow(name(saved.name, changed = current && savedFlows.changedSinceSaved(state)), steps(saved.steps.size), current, filled = true)
            }
        }

    /** Every widget of the tab the state changes: status lines and each row. */
    fun updates(state: IdleState): List<WidgetUpdate> {
        val status = status(state)
        return listOf(
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_1, status[0]),
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_2, status[1]),
            WidgetUpdate.Text(FlowWidgets.TAB_STATUS_3, status[2]),
        ) + rows(state).withIndex().flatMap { (slot, row) ->
            listOf(
                WidgetUpdate.Text(FlowWidgets.rowName(slot), row.name),
                WidgetUpdate.Text(FlowWidgets.rowCount(slot), row.count),
                WidgetUpdate.Visible(FlowWidgets.rowFrameLayer(slot), visible = row.current),
                WidgetUpdate.Visible(FlowWidgets.rowLoadLayer(slot), visible = row.filled),
                WidgetUpdate.Visible(FlowWidgets.rowNewLayer(slot), visible = !row.filled),
                WidgetUpdate.Visible(FlowWidgets.rowDeleteLayer(slot), visible = row.filled),
            )
        }
    }

    /** The flow line's words; the step count is one, so a line never ends on its number. */
    private fun flow(state: IdleState): List<String> {
        val label = savedFlows.label(state)
        if (label == null && state.steps.isEmpty()) return listOf("Flow:", "empty")
        return "Flow: ${label ?: "not saved"},".split(" ") + steps(state.steps.size)
    }

    /** The name as it fits the row, "(changed)" kept whole after it in yellow. */
    private fun name(name: String, changed: Boolean): String {
        val room = FlowWidgets.nameRoom(slots)
        if (!changed) return small.fit(name, room)
        return small.fit(name, room - small.width(" $CHANGED")) + " @yel@$CHANGED"
    }

    private fun steps(count: Int): String = if (count == 1) "1 step" else "$count steps"

    companion object {
        private const val FLOW_LINES = 2
        private const val CHANGED = "(changed)"
        private const val EMPTY = "@gry@empty"

        /** The tab for [savedFlowSlots] saved-flow slots, measured in the fonts of the client's [cache]. */
        fun fromCache(cache: Cache, savedFlowSlots: Int): IdleTab =
            IdleTab(SavedFlows(savedFlowSlots), ClientFont.fromCache(cache, ClientFont.PLAIN), ClientFont.fromCache(cache))
    }
}
