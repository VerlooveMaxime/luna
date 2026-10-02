package game.idle.ui

import game.idle.IdleState

/** The lines the status overlay shows for a player's [IdleState]: nothing while the autopilot is off. */
object AutopilotStatus {

    fun text(state: IdleState): String {
        if (!state.running) return ""
        val header = "@gre@Autopilot@whi@ step ${state.stepIndex + 1}/${state.flow.size}"
        val step = state.flow.getOrNull(state.stepIndex)?.let { "@yel@${plain(it)}" }
        return listOfNotNull(header, step).joinToString(StatusOverlayMessageWriter.LINE_SEPARATOR.toString())
    }

    /** The three lines of the sidebar tab, which is about 28 characters wide. */
    fun tabLines(state: IdleState): List<String> {
        if (!state.running) return listOf("Autopilot: off", "", "")
        val step = state.flow.getOrNull(state.stepIndex) ?: ""
        return listOf("Autopilot: running", "Step ${state.stepIndex + 1}/${state.flow.size}", shortened(step))
    }

    private fun shortened(line: String): String =
        if (line.length <= TAB_WIDTH) line else line.take(TAB_WIDTH - 2) + ".."

    private const val TAB_WIDTH = 28

    /** A flow line is typed by the player, so it must not carry the separator or the string terminator. */
    private fun plain(line: String): String =
        line.replace(StatusOverlayMessageWriter.LINE_SEPARATOR, ' ').replace('\n', ' ')
}
