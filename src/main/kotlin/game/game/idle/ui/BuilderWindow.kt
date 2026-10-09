package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.idleState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.InterfaceMessageWriter

/** The builder's screens as a Luna window; Luna calls [onOpen] before [open], so they are filled before they show. */
class BuilderInterface(private val fill: (Player) -> Unit) : AbstractOverlay(OverlayType.WIDGET_STANDARD) {

    override fun open(player: Player) {
        player.queue(InterfaceMessageWriter(BuilderWidgets.ROOT))
    }

    override fun onOpen(player: Player) = fill(player)
}

/**
 * Shows the builder's screens to Luna players, each with room for [slots] steps: the client is told the slot count,
 * then the window opens on the overview, which follows every change of the player's idle state while it is open.
 */
class BuilderWindow(private val overview: BuilderOverview, private val slots: Int) {

    fun open(player: Player) {
        player.queue(BuilderSlotsMessageWriter(slots))
        player.overlays.open(BuilderInterface { show(it, BuilderPage.OVERVIEW) })
    }

    /** Called on every idle state change: the open overview follows it. */
    fun refresh(player: Player, state: IdleState) {
        if (isOpen(player)) WidgetUpdate.send(player, overview.updates(state, slots))
    }

    /** Fills every screen and shows [page]; the client rebuilds the window blank each time it opens. */
    fun show(player: Player, page: BuilderPage) {
        val state = player.idleState
        WidgetUpdate.send(player, overview.updates(state, slots) + overview.kinds(state) + overview.page(page))
    }

    fun isOpen(player: Player): Boolean = player.overlays.has(BuilderInterface::class.java)
}

/** Routes a Luna player's clicks and drags on the builder's screens to the [BuilderScreen] and shows the answer. */
class LunaBuilderUi(private val screen: BuilderScreen<LunaAutopilotPlayer>, private val window: BuilderWindow, private val ui: IdleUi) {

    fun click(player: Player, widgetId: Int) = answer(player, screen.click(LunaAutopilotPlayer(player, ui), widgetId))

    fun arrange(player: Player, from: Int, to: Int) = answer(player, screen.arrange(LunaAutopilotPlayer(player, ui), from, to))

    // An expression `when`, so JaCoCo sees every branch (see coverage notes on statement `when`).
    private fun answer(player: Player, answer: BuilderAnswer): Unit =
        when (answer) {
            BuilderAnswer.Ignored -> Unit
            BuilderAnswer.Close -> player.overlays.closeWindows()
            is BuilderAnswer.Show -> show(player, answer)
        }

    /** The window shows the page asked for, or the state as it now is; a click from a closed window only speaks. */
    private fun show(player: Player, answer: BuilderAnswer.Show) {
        val page = answer.page
        if (window.isOpen(player) && page != null) window.show(player, page) else window.refresh(player, player.idleState)
        if (answer.message.isNotEmpty()) player.sendMessage(answer.message)
    }
}
