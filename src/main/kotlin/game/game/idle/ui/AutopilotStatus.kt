package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings

/** The lines the status overlay and the tab show for a player's [IdleState], each step worded by [summary]. */
class AutopilotStatus(private val summary: (StepSettings) -> String) {

    /** Nothing while the autopilot is off. */
    fun text(state: IdleState): String {
        if (!state.running) return ""
        val header = "@gre@Autopilot@whi@ step ${state.stepIndex + 1}/${state.steps.size}"
        val step = current(state)?.let { "@yel@${plain(it)}" }
        return listOfNotNull(header, step).joinToString(StatusOverlayMessageWriter.LINE_SEPARATOR.toString())
    }

    /** The three lines of the sidebar tab, which is about 28 characters wide. */
    fun tabLines(state: IdleState): List<String> {
        if (!state.running) return listOf("Autopilot: off", "", "")
        return listOf("Autopilot: running", "Step ${state.stepIndex + 1}/${state.steps.size}", shortened(current(state) ?: ""))
    }

    private fun current(state: IdleState): String? = state.steps.getOrNull(state.stepIndex)?.let(summary)

    private fun shortened(line: String): String =
        if (line.length <= TAB_WIDTH) line else line.take(TAB_WIDTH - 2) + ".."

    /** Settings can come from outside the game (the harness), so a summary must not carry the separator or the string terminator. */
    private fun plain(line: String): String =
        line.replace(StatusOverlayMessageWriter.LINE_SEPARATOR, ' ').replace('\n', ' ')

    private companion object {
        const val TAB_WIDTH = 28
    }
}
