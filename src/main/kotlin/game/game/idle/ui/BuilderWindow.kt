package game.idle.ui

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.IdleState
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.flow.SavedFlows
import game.idle.flow.option.LunaOptionFacts
import game.idle.idleState
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.InterfaceMessageWriter

/** The step a player configures on the builder's open window; never saved, dropped when the window closes. */
private var Player.builderDraft by Attr.nullableObj(StepDraft::class)

/**
 * The builder's screens as a Luna window; Luna calls [onOpen] before [open], so they are filled before they show. The
 * step being configured goes with the window.
 */
class BuilderInterface(private val fill: (Player) -> Unit) : AbstractOverlay(OverlayType.WIDGET_STANDARD) {

    override fun open(player: Player) {
        player.queue(InterfaceMessageWriter(BuilderWidgets.ROOT))
    }

    override fun onOpen(player: Player) = fill(player)

    override fun onClose(player: Player) {
        player.builderDraft = null
    }
}

/** The client's "Enter amount" prompt for the configure screen: [onAmount] gets the number typed. */
class AmountInput(private val onAmount: (Player, Int) -> Unit) : NumberInput() {

    override fun input(player: Player, value: Int) = onAmount(player, value)
}

/**
 * Shows the builder's screens to Luna players, each with room for [slots] steps: the client is told the slot count,
 * then the window opens on the overview, which follows every change of the player's idle state while it is open, as
 * the configure screen of the step being configured does.
 */
class BuilderWindow(private val overview: BuilderOverview, private val configure: BuilderConfigure, private val slots: Int) {

    fun open(player: Player) {
        player.queue(SlotCountMessageWriter(SlotCountMessageWriter.STEP_SLOTS, slots))
        player.overlays.open(BuilderInterface { show(it, BuilderPage.OVERVIEW) })
    }

    /** Called on every idle state change: the open screens follow it. */
    fun refresh(player: Player, state: IdleState) {
        if (!isOpen(player)) return
        WidgetUpdate.send(player, overview.updates(state, slots))
        draft(player)?.let { WidgetUpdate.send(player, configure.updates(state, it, LunaOptionFacts.of(player))) }
    }

    /** Fills the overview and the kind picker and shows [page], dropping the step being configured. */
    fun show(player: Player, page: BuilderPage) {
        player.builderDraft = null
        val state = player.idleState
        WidgetUpdate.send(player, overview.updates(state, slots) + overview.kinds(state) + overview.page(page))
    }

    /** Shows the configure screen of [draft], which becomes the step being configured. */
    fun configure(player: Player, draft: StepDraft) {
        player.builderDraft = draft
        WidgetUpdate.send(player, configure.updates(player.idleState, draft, LunaOptionFacts.of(player)) + overview.page(BuilderPage.CONFIGURE))
    }

    /** The step being configured, null when none is. */
    fun draft(player: Player): StepDraft? = player.builderDraft

    fun isOpen(player: Player): Boolean = player.overlays.has(BuilderInterface::class.java)
}

/**
 * Routes a Luna player's clicks, drags, picks, typed amounts and names on the builder's screens and the Idle tab to the
 * [BuilderScreen] and shows the answer; searches measure their labels with the client's [font].
 */
class LunaBuilderUi(
    private val screen: BuilderScreen<LunaAutopilotPlayer>,
    private val window: BuilderWindow,
    private val ui: IdleUi,
    private val font: ClientFont,
) {

    fun click(player: Player, widgetId: Int) = answer(player, screen.click(autopilotPlayer(player), widgetId, window.draft(player)))

    fun tab(player: Player, widgetId: Int) = answer(player, screen.tab(autopilotPlayer(player), widgetId))

    fun arrange(player: Player, from: Int, to: Int) = answer(player, screen.arrange(autopilotPlayer(player), from, to))

    /** A tile the player picked on the world map the configure screen opened. */
    fun picked(player: Player, tile: Tile) = answer(player, screen.pickedTile(window.draft(player), tile))

    private fun autopilotPlayer(player: Player) = LunaAutopilotPlayer(player, ui)

    // An expression `when`, so JaCoCo sees every branch (see coverage notes on statement `when`).
    private fun answer(player: Player, answer: BuilderAnswer): Unit =
        when (answer) {
            BuilderAnswer.Ignored -> Unit
            BuilderAnswer.Close -> player.overlays.closeWindows()
            BuilderAnswer.Open -> open(player)
            is BuilderAnswer.Show -> show(player, answer)
            is BuilderAnswer.Configure -> configure(player, answer.draft, answer.message)
            is BuilderAnswer.Search -> search(player, answer)
            is BuilderAnswer.Amount -> amount(player, answer.draft)
            is BuilderAnswer.PickTile -> pickTile(player, answer)
            is BuilderAnswer.Replaced -> replaced(player, answer.message)
            is BuilderAnswer.Name -> name(player, answer)
        }

    /** The window shows the page asked for, or the state as it now is; a click from a closed window only speaks. */
    private fun show(player: Player, answer: BuilderAnswer.Show) {
        val page = answer.page
        if (window.isOpen(player) && page != null) window.show(player, page) else window.refresh(player, player.idleState)
        tell(player, answer.message)
    }

    /** A pick or a typed amount answering a window closed since is dropped: [BuilderInterface] forgets the draft. */
    private fun configure(player: Player, draft: StepDraft, message: String) {
        if (window.isOpen(player)) window.configure(player, draft)
        tell(player, message)
    }

    private fun search(player: Player, answer: BuilderAnswer.Search) {
        configure(player, answer.draft, message = "")
        val field = answer.field
        val context = answer.context.copy(facts = LunaOptionFacts.of(player), here = Tile.of(player.position))
        SearchPrompts.open(player, field.title, field.target.source.options(context), font) { plr, option ->
            answer(plr, screen.picked(window.draft(plr), answer.draft, field.target.key, option.value))
        }
    }

    private fun amount(player: Player, draft: StepDraft) {
        configure(player, draft, message = "")
        player.overlays.open(AmountInput { plr, value -> answer(plr, screen.typed(window.draft(plr), value)) })
    }

    private fun pickTile(player: Player, answer: BuilderAnswer.PickTile) {
        configure(player, answer.draft, message = "")
        player.queue(MapPickMessageWriter(answer.centre.x, answer.centre.y))
    }

    /** An open builder stays on the screen it shows, a step being configured included. */
    private fun open(player: Player) {
        if (!window.isOpen(player)) window.open(player)
    }

    /** The new current flow replaces the one an open builder showed, a step being configured with it. */
    private fun replaced(player: Player, message: String) {
        if (window.isOpen(player)) window.show(player, BuilderPage.OVERVIEW) else window.open(player)
        tell(player, message)
    }

    private fun name(player: Player, answer: BuilderAnswer.Name) {
        SearchPrompts.openName(player, answer.title, answer.text, SavedFlows.MAX_NAME) { plr, name ->
            answer(plr, screen.named(autopilotPlayer(plr), answer.slot, name))
        }
    }

    private fun tell(player: Player, message: String) {
        if (message.isNotEmpty()) player.sendMessage(message)
    }
}
