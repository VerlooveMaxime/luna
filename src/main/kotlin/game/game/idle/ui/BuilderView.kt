package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.ui.FlowWidgets.DRAFT_ADD
import game.idle.ui.FlowWidgets.DRAFT_FIELDS
import game.idle.ui.FlowWidgets.DRAFT_FIELD_LABELS
import game.idle.ui.FlowWidgets.DRAFT_KIND
import game.idle.ui.FlowWidgets.DRAFT_LABEL
import game.idle.ui.FlowWidgets.MESSAGE
import game.idle.ui.FlowWidgets.ROWS
import game.idle.ui.FlowWidgets.STATUS
import game.idle.ui.FlowWidgets.TAB_STATUS_1
import game.idle.ui.FlowWidgets.TAB_STATUS_2
import game.idle.ui.FlowWidgets.TAB_STATUS_3

/** The texts of the IdleRS widgets, keyed by widget id, for a given state of the player; [summary] words a step. */
class BuilderView(private val summary: (StepSettings) -> String) {

    private val autopilotStatus = AutopilotStatus(summary)

    /** [editing] is the row loaded into the draft, null while the draft is a new step. */
    fun texts(state: IdleState, draft: FlowDraft, message: String, editing: Int?): Map<Int, String> =
        stateTexts(state) + draftTexts(draft, editing) + (MESSAGE to message)

    /** The part of the builder that follows the idle state alone: the step rows and the status line. */
    fun stateTexts(state: IdleState): Map<Int, String> = buildMap {
        for (row in 0 until ROWS) {
            val line = state.steps.getOrNull(row)?.let(summary)
            put(FlowWidgets.rowText(row), rowLabel(state, row, line))
            put(FlowWidgets.rowUp(row), if (line == null) "" else "up")
            put(FlowWidgets.rowDown(row), if (line == null) "" else "down")
            put(FlowWidgets.rowDelete(row), if (line == null) "" else "del")
        }
        put(STATUS, status(state))
    }

    /** Field slots the draft's kind of step does not use stay blank, label included. */
    fun draftTexts(draft: FlowDraft, editing: Int?): Map<Int, String> = buildMap {
        put(DRAFT_LABEL, (if (editing == null) "New step" else "Editing step ${editing + 1}") + " (click a field to change it)")
        put(DRAFT_KIND, draft.type.label)
        DRAFT_FIELDS.forEachIndexed { index, id ->
            val field = draft.type.fields.getOrNull(index)
            put(DRAFT_FIELD_LABELS[index], field?.label ?: "")
            put(id, field?.let { shown(it, draft.value(index)) } ?: "")
        }
        put(DRAFT_ADD, if (editing == null) "Add step" else "Save step ${editing + 1}")
    }

    fun tabTexts(state: IdleState): Map<Int, String> {
        val lines = autopilotStatus.tabLines(state)
        return mapOf(TAB_STATUS_1 to lines[0], TAB_STATUS_2 to lines[1], TAB_STATUS_3 to lines[2])
    }

    private fun shown(field: StepField, value: String): String =
        when (field) {
            is StepField.Choice -> field.display(value)
            is StepField.MapTile -> value
        }.ifEmpty { "-" }

    private fun rowLabel(state: IdleState, row: Int, line: String?): String =
        when {
            line == null -> ""
            state.running && state.stepIndex == row -> "@gre@${row + 1}. $line"
            else -> "${row + 1}. $line"
        }

    private fun status(state: IdleState): String =
        when {
            state.steps.isEmpty() -> "No steps yet."
            state.running -> "@gre@Running step ${state.stepIndex + 1}/${state.steps.size}"
            else -> "Stopped. ${state.steps.size} steps."
        }
}
