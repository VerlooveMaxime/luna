package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * Sends the IdleRS widgets to a Luna player: the Idle tab at login, and every text that follows the idle state, each
 * step worded by [summary]; the [builder]'s screens follow it too.
 */
class IdleUi(summary: (StepSettings) -> String, private val builder: BuilderWindow? = null) {

    private val status = AutopilotStatus(summary)

    fun installTab(player: Player, state: IdleState) {
        player.tabs.set(TabIndex.UNUSED, FlowWidgets.TAB)
        sendTexts(player, tabTexts(state))
    }

    /** Called on every idle state change: overlay, tab lines, and the builder while it is open. */
    fun refresh(player: Player, state: IdleState) {
        player.queue(StatusOverlayMessageWriter(status.text(state)))
        sendTexts(player, tabTexts(state))
        builder?.refresh(player, state)
    }

    private fun tabTexts(state: IdleState): Map<Int, String> {
        val lines = status.tabLines(state)
        return mapOf(FlowWidgets.TAB_STATUS_1 to lines[0], FlowWidgets.TAB_STATUS_2 to lines[1], FlowWidgets.TAB_STATUS_3 to lines[2])
    }

    companion object {
        /**
         * Not `player.sendText`: Luna skips a text equal to the last one it sent for that id, but the client rebuilds
         * our widgets blank each time the window closes, so every text must go out every time (found live 2026-10-01).
         */
        fun sendTexts(player: Player, texts: Map<Int, String>) =
            texts.forEach { (id, text) -> player.queue(WidgetTextMessageWriter(text, id)) }
    }
}
