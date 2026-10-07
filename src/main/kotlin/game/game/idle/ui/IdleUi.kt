package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.flow.StepSettings
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.InterfaceMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * The flow builder screen as a Luna window. Not a `StandardInterface`: that looks the id up in the cache widget
 * definitions, which know nothing of widgets the client defines in code. Luna calls [onOpen] before [open], so the
 * texts reach the client before the screen shows.
 */
class FlowBuilderInterface(private val texts: () -> Map<Int, String>) : AbstractOverlay(OverlayType.WIDGET_STANDARD) {

    override fun open(player: Player) {
        player.queue(InterfaceMessageWriter(FlowWidgets.BUILDER))
    }

    override fun onOpen(player: Player) = IdleUi.sendTexts(player, texts())
}

/**
 * Sends the IdleRS widgets to a Luna player: the tab at login, and every text that follows the idle state, each step
 * worded by [summary].
 */
class IdleUi(summary: (StepSettings) -> String) {

    private val view = BuilderView(summary)

    private val status = AutopilotStatus(summary)

    fun installTab(player: Player, state: IdleState) {
        player.tabs.set(TabIndex.UNUSED, FlowWidgets.TAB)
        sendTexts(player, view.tabTexts(state))
    }

    /** Called on every idle state change: overlay, tab lines, and the builder's rows while it is open. */
    fun refresh(player: Player, state: IdleState) {
        player.queue(StatusOverlayMessageWriter(status.text(state)))
        sendTexts(player, view.tabTexts(state))
        if (player.overlays.has(FlowBuilderInterface::class.java)) sendTexts(player, view.stateTexts(state))
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

/** Routes clicks on IdleRS widgets from a Luna player to the [FlowBuilder] and shows what changed. */
class LunaFlowUi(private val builder: FlowBuilder<LunaAutopilotPlayer>, private val ui: IdleUi) {

    fun click(player: Player, widgetId: Int) {
        val autopilotPlayer = LunaAutopilotPlayer(player, ui)
        act(player, autopilotPlayer, builder.click(autopilotPlayer, widgetId))
    }

    /** A tile the player picked on the world map the builder opened. */
    fun picked(player: Player, tile: Tile) {
        val autopilotPlayer = LunaAutopilotPlayer(player, ui)
        act(player, autopilotPlayer, builder.picked(autopilotPlayer, tile))
    }

    // An expression `when`, so JaCoCo sees every branch (see coverage notes on statement `when`).
    private fun act(player: Player, autopilotPlayer: LunaAutopilotPlayer, result: ClickResult): Unit =
        when (result) {
            ClickResult.Ignored -> Unit
            ClickResult.Open -> player.overlays.open(FlowBuilderInterface { builder.texts(autopilotPlayer) })
            ClickResult.Close -> player.overlays.closeWindows()
            ClickResult.Refresh -> show(player, autopilotPlayer)
            is ClickResult.PickTile -> player.queue(MapPickMessageWriter(result.centre.x, result.centre.y))
        }

    fun forget(player: Player) = builder.forget(LunaAutopilotPlayer(player, ui))

    /** The builder shows its own message line; a click from the tab while it is closed speaks in the chat box. */
    private fun show(player: Player, autopilotPlayer: LunaAutopilotPlayer) {
        if (player.overlays.has(FlowBuilderInterface::class.java)) {
            IdleUi.sendTexts(player, builder.texts(autopilotPlayer))
        } else if (builder.message(autopilotPlayer).isNotEmpty()) {
            player.sendMessage(builder.message(autopilotPlayer))
        }
    }
}
