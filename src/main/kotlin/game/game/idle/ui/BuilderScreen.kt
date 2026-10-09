package game.idle.ui

import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotPlayer
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.location.Tile

/** What the game shows after a click on the builder's screens or the Idle tab. */
sealed interface BuilderAnswer {

    data object Ignored : BuilderAnswer

    data object Close : BuilderAnswer

    /** Opens the builder on its overview. */
    data object Open : BuilderAnswer

    /**
     * Shows [page] (the current one when null) with the state as it now is, and says [message] in the chat box. Another
     * page than the configure screen drops the step being configured.
     */
    data class Show(val page: BuilderPage? = null, val message: String = "") : BuilderAnswer

    /** Shows the configure screen for [draft], and says [message] in the chat box. */
    data class Configure(val draft: StepDraft, val message: String = "") : BuilderAnswer

    /**
     * Shows [draft] and opens the chatbox search for its [field], over the options its target offers in [context] (the
     * caller adds the player's facts and tile).
     */
    data class Search(val draft: StepDraft, val field: StepField.Search, val context: OptionContext) : BuilderAnswer

    /** Shows [draft], the field it types framed, and opens the client's "Enter amount" prompt. */
    data class Amount(val draft: StepDraft) : BuilderAnswer

    /** Shows [draft] and opens the world map for a tile, centred on [centre]. */
    data class PickTile(val draft: StepDraft, val centre: Tile) : BuilderAnswer
}

/**
 * The clicks and drags of the builder's screens and the Idle tab (flow builder v2, S06), over the flow saved in the
 * player's state and the [Autopilot] that runs it; a flow has room for [slots] steps. A step is changed on its
 * configure screen as a [StepDraft], which joins the flow on Save. While the flow runs nothing is changed (Maxime,
 * 2026-10-09): a configure screen opens to look. Messages go to the chat box.
 */
class BuilderScreen<P : AutopilotPlayer>(
    private val autopilot: Autopilot<P>,
    private val resolver: FlowResolver,
    private val names: GameNames,
    private val slots: Int,
) {

    private val types = resolver.types.all

    /** A click on the builder's screens; [draft] is the step being configured, null when none is. */
    fun click(player: P, widgetId: Int, draft: StepDraft?): BuilderAnswer =
        when (widgetId) {
            BuilderWidgets.CLOSE -> BuilderAnswer.Close
            BuilderWidgets.RUN -> run(player)
            BuilderWidgets.STOP -> stop(player)
            BuilderWidgets.CLEAR -> clear(player)
            BuilderWidgets.BASE_LEVELS -> levels(player, boosted = false)
            BuilderWidgets.BOOSTED_LEVELS -> levels(player, boosted = true)
            BuilderWidgets.KINDS_BACK -> BuilderAnswer.Show(BuilderPage.OVERVIEW)
            else -> BuilderWidgets.slotOf(widgetId)?.let { slot(player, it) }
                ?: BuilderWidgets.kindOf(widgetId)?.let { kind(player, it) }
                ?: draft?.let { configure(player, widgetId, it) }
                ?: BuilderAnswer.Ignored
        }

    /** A click on the Idle tab: the builder, Run and Stop. */
    fun tab(player: P, widgetId: Int): BuilderAnswer =
        when (widgetId) {
            FlowWidgets.TAB_OPEN_BUILDER -> BuilderAnswer.Open
            FlowWidgets.TAB_RUN -> run(player)
            FlowWidgets.TAB_STOP -> stop(player)
            else -> BuilderAnswer.Ignored
        }

    /** A slot dragged onto another: it moves there, the steps in between shifting by one; dropped past them it goes last. */
    fun arrange(player: P, from: Int, to: Int): BuilderAnswer {
        val steps = player.idleState.steps
        if (from !in steps.indices) return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        val moved = steps.toMutableList().apply { add(to.coerceAtMost(steps.lastIndex).coerceAtLeast(0), removeAt(from)) }
        player.idleState = player.idleState.withFlow(moved)
        return BuilderAnswer.Show()
    }

    /**
     * An option picked in the search [opened] asked for, setting [key] to [value]; [draft] is the step configured now,
     * which a pick for another step leaves alone.
     */
    fun picked(draft: StepDraft?, opened: StepDraft, key: String, value: String): BuilderAnswer =
        draft?.takeIf { it.sameStep(opened) }?.let { BuilderAnswer.Configure(it.with(key, value)) } ?: BuilderAnswer.Ignored

    /** [value] entered on the "Enter amount" prompt [draft] opened; out of its field's range it says the rule instead. */
    fun typed(draft: StepDraft?, value: Int): BuilderAnswer {
        val field = draft?.let { fields(it).filterIsInstance<StepField.Typed>().firstOrNull { field -> field.key == it.typing } }
            ?: return BuilderAnswer.Ignored
        if (value !in field.range) return BuilderAnswer.Configure(draft.notTyping(), "$PREFIX ${field.rule}.")
        return BuilderAnswer.Configure(draft.with(field.key, value.toString()))
    }

    /** A tile picked on the world map: it goes into [draft]'s tile, if it has one. */
    fun pickedTile(draft: StepDraft?, tile: Tile): BuilderAnswer {
        val field = draft?.let { fields(it).filterIsInstance<StepField.MapTile>().firstOrNull() } ?: return BuilderAnswer.Ignored
        return BuilderAnswer.Configure(draft.with(field.key, tile.text()))
    }

    private fun run(player: P): BuilderAnswer {
        val steps = player.idleState.steps
        if (steps.isEmpty()) return say("the flow is empty. Add a step first.")
        val cannot = resolver.problems(steps).indexOfFirst { it != null }
        if (cannot >= 0) return BuilderAnswer.Show(message = Autopilot.cannotWork(cannot + 1))
        player.idleState = player.idleState.fromStart()
        autopilot.start(player)
        return say("running from step 1.")
    }

    private fun stop(player: P): BuilderAnswer {
        autopilot.stop(player)
        return say("stopped.")
    }

    private fun clear(player: P): BuilderAnswer {
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        if (player.idleState.steps.isEmpty()) return BuilderAnswer.Show()
        player.idleState = player.idleState.withFlow(emptyList())
        return say("flow cleared.")
    }

    private fun levels(player: P, boosted: Boolean): BuilderAnswer {
        player.idleState = player.idleState.copy(countBoostedLevels = boosted)
        return BuilderAnswer.Show()
    }

    /** A step's slot opens its configure screen, to look at while the flow runs; the first free slot opens the kind picker. */
    private fun slot(player: P, slot: Int): BuilderAnswer {
        val steps = player.idleState.steps
        steps.getOrNull(slot)?.let { return BuilderAnswer.Configure(StepDraft(slot, it, new = false)) }
        if (slot != steps.size || slot >= slots) return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        return BuilderAnswer.Show(BuilderPage.KINDS)
    }

    /** A kind picked: a new step of it, numbered after the last, which joins the flow on Save (Maxime, 2026-10-09). */
    private fun kind(player: P, kind: Int): BuilderAnswer {
        val type = types.getOrNull(kind) ?: return BuilderAnswer.Ignored
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(BuilderPage.OVERVIEW, STOP_FIRST)
        return BuilderAnswer.Configure(StepDraft(player.idleState.steps.size, StepSettings(type.kind), new = true))
    }

    /** A click on the configure screen of [draft]; any click closes the client's "Enter amount" prompt. */
    private fun configure(player: P, widgetId: Int, draft: StepDraft): BuilderAnswer? {
        val running = autopilot.isRunning(player)
        val stopFirst = BuilderAnswer.Configure(draft.notTyping(), STOP_FIRST)
        return when (widgetId) {
            BuilderWidgets.BACK -> BuilderAnswer.Show(BuilderPage.OVERVIEW)
            BuilderWidgets.SAVE -> if (running) stopFirst else save(player, draft)
            BuilderWidgets.DELETE -> if (draft.new) BuilderAnswer.Configure(draft.notTyping()) else if (running) stopFirst else delete(player, draft)
            else -> BuilderWidgets.fieldOf(widgetId)?.let { row -> if (running) stopFirst else field(player, draft, row) }
                ?: BuilderWidgets.buttonOf(widgetId)?.let { row -> if (running) stopFirst else unbounded(draft, row) }
        }
    }

    private fun save(player: P, draft: StepDraft): BuilderAnswer {
        val steps = player.idleState.steps
        // A draft's slot is never negative, so one comparison says whether the step is still there.
        val replacing = !draft.new && draft.slot < steps.size
        if (!replacing && steps.size >= slots) return BuilderAnswer.Configure(draft.notTyping(), "$PREFIX the flow has room for $slots steps.")
        val slot = if (replacing) draft.slot else steps.size
        val saved = if (replacing) steps.toMutableList().apply { set(slot, draft.settings) } else steps + draft.settings
        player.idleState = player.idleState.withFlow(saved)
        return BuilderAnswer.Show(BuilderPage.OVERVIEW, "$PREFIX step ${slot + 1} saved.")
    }

    private fun delete(player: P, draft: StepDraft): BuilderAnswer {
        val steps = player.idleState.steps
        if (draft.slot >= steps.size) return BuilderAnswer.Show(BuilderPage.OVERVIEW)
        player.idleState = player.idleState.withFlow(steps.filterIndexed { index, _ -> index != draft.slot })
        return BuilderAnswer.Show(BuilderPage.OVERVIEW, "$PREFIX step ${draft.slot + 1} deleted.")
    }

    private fun field(player: P, draft: StepDraft, row: Int): BuilderAnswer? {
        val idle = draft.notTyping()
        // The empty arm goes first: last, JaCoCo counts a branch no test can reach (coverage notes).
        return when (val field = ConfigureRows.of(fields(draft))[row]) {
            is StepField.Note, null -> null
            is StepField.Search -> BuilderAnswer.Search(idle, field, searchContext(player, draft))
            is StepField.Typed -> BuilderAnswer.Amount(draft.copy(typing = field.key))
            is StepField.MapTile -> BuilderAnswer.PickTile(idle, tileOf(draft.settings[field.key]) ?: player.tile)
        }
    }

    /** The button that removes a typed amount, so the step does as much as it can. */
    private fun unbounded(draft: StepDraft, row: Int): BuilderAnswer? =
        (ConfigureRows.of(fields(draft))[row] as? StepField.Typed)?.let { BuilderAnswer.Configure(draft.with(it.key, "")) }

    /**
     * The search's options are worked out from the steps before the one configured and its settings as edited. Until S07
     * gives processing steps their input setting, they offer everything with the bank's counts (Maxime, 2026-10-09).
     */
    private fun searchContext(player: P, draft: StepDraft): OptionContext =
        OptionContext(settings = draft.settings, before = resolver.contextBefore(player.idleState.steps, draft.slot), input = InputSource.BANK)

    private fun fields(draft: StepDraft): List<StepField> = resolver.types.find(draft.settings.kind)?.fields(names).orEmpty()

    /** The tile a map field holds ("x y"), null when it holds none. */
    private fun tileOf(text: String?): Tile? =
        text?.split(" ")?.mapNotNull(String::toIntOrNull)?.takeIf { it.size >= 2 }?.let { (x, y) -> Tile(x, y) }

    private fun say(message: String): BuilderAnswer = BuilderAnswer.Show(message = "$PREFIX $message")

    companion object {
        const val PREFIX = "Autopilot:"
        const val STOP_FIRST = "$PREFIX stop the flow before editing it."
    }
}
