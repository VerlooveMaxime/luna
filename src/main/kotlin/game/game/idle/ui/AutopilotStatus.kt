package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepLines
import game.idle.font.ClientFont

/**
 * The lines the status overlay shows for a player's [IdleState]: the step under way as its slot words it ([lines],
 * Maxime, 2026-10-11, Q6), cut to the overlay's width in its [font].
 */
class AutopilotStatus(private val lines: StepLines, private val font: ClientFont) {

    /** Nothing while the autopilot is off; a blocked step adds why in red, a waiting one what it waits for in yellow. */
    fun text(state: IdleState): String {
        if (!state.running) return ""
        val header = "@gre@Autopilot@whi@ step ${state.stepIndex + 1}/${state.steps.size}"
        val step = current(state)?.let { "@yel@${plain(it)}" }
        val blocked = state.blocked?.let { "@red@${plain(it)}" }
        val waiting = state.waiting?.let { "@yel@${plain(it)}" }
        return listOfNotNull(header, step, blocked, waiting).joinToString(StatusOverlayMessageWriter.LINE_SEPARATOR.toString())
    }

    private fun current(state: IdleState): String? =
        state.steps.getOrNull(state.stepIndex)?.let { font.fit(lines.of(state.steps, state.stepIndex, state.progress.met).line, ROOM) }

    /** Settings can come from outside the game (the harness), so a line must not carry the separator or the string terminator. */
    private fun plain(line: String): String =
        line.replace(StatusOverlayMessageWriter.LINE_SEPARATOR, ' ').replace('\n', ' ')

    companion object {
        /** The overlay's lines end 5 px from the game view's right edge (`StatusOverlay.RIGHT`) and may start 4 px from its left. */
        const val ROOM = 503
    }
}
