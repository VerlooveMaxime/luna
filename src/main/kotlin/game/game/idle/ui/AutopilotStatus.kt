package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings

/** The lines the status overlay and the tab show for a player's [IdleState], each step worded by [summary]. */
class AutopilotStatus(private val summary: (StepSettings) -> String) {

    /** Nothing while the autopilot is off; a blocked step adds why in red. */
    fun text(state: IdleState): String {
        if (!state.running) return ""
        val header = "@gre@Autopilot@whi@ step ${state.stepIndex + 1}/${state.steps.size}"
        val step = current(state)?.let { "@yel@${plain(it)}" }
        val blocked = state.blocked?.let { "@red@${plain(reason(it))}" }
        return listOfNotNull(header, step, blocked).joinToString(StatusOverlayMessageWriter.LINE_SEPARATOR.toString())
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

    companion object {
        private const val TAB_WIDTH = 28

        private const val CHAT_PREFIX = "Autopilot: "

        /** A block as a status line: without its "Autopilot: " and starting with a capital. */
        fun reason(blocked: String): String = blocked.removePrefix(CHAT_PREFIX).let { it.take(1).uppercase() + it.drop(1) }
    }
}
