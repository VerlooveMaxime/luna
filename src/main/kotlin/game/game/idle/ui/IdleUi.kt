package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Sends the IdleRS widgets to a Luna player: the Idle tab at login, and every widget that follows the idle state: the
 * status overlay, each step worded by [summary], the [tab]'s lines and saved flows, and the [builder]'s screens.
 */
class IdleUi(summary: (StepSettings) -> String, private val tab: IdleTab, private val builder: BuilderWindow? = null) {

    private val status = AutopilotStatus(summary)

    /** The client builds the tab's rows for the player's saved-flow slots, so it hears of them before the tab. */
    fun installTab(player: Player, state: IdleState) {
        player.queue(SlotCountMessageWriter(SlotCountMessageWriter.SAVED_FLOW_SLOTS, tab.slots))
        player.tabs.set(TabIndex.UNUSED, FlowWidgets.TAB)
        WidgetUpdate.send(player, tab.updates(state))
    }

    /** Called on every idle state change: overlay, tab, and the builder while it is open. */
    fun refresh(player: Player, state: IdleState) {
        player.queue(StatusOverlayMessageWriter(status.text(state)))
        WidgetUpdate.send(player, tab.updates(state))
        builder?.refresh(player, state)
    }
}
