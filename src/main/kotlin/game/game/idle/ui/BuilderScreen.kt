package game.idle.ui

import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotPlayer
import game.idle.flow.FlowError
import game.idle.flow.FlowIds
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexResolver
import game.idle.flow.SavedFlows
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepItems
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.location.Tile

/** What the game shows after a click on the builder's screens or the Idle tab. */
sealed interface BuilderAnswer {

    data object Ignored : BuilderAnswer

    data object Close : BuilderAnswer

    /** Opens the builder on its overview; an open builder stays as it is. */
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

    /**
     * Shows [draft] and opens the chatbox search that stays open for its list [field], over the options its source
     * offers in [context] (the caller adds the player's facts and tile): each row clicked is added or taken out.
     */
    data class Several(val draft: StepDraft, val field: StepField.Items, val context: OptionContext) : BuilderAnswer

    /** Shows [draft], the field it types framed, and opens the client's "Enter amount" prompt. */
    data class Amount(val draft: StepDraft) : BuilderAnswer

    /** Shows [draft] and opens the world map for a tile, centred on [centre]. */
    data class PickTile(val draft: StepDraft, val centre: Tile) : BuilderAnswer

    /**
     * The current flow was replaced, loaded or new: the builder opens, or an open one goes back to its overview, and
     * [message] goes to the chat box.
     */
    data class Replaced(val message: String = "") : BuilderAnswer

    /** Opens the name prompt for saving the flow in saved-flow [slot]: [title] over a line starting with [text]. */
    data class Name(val slot: Int, val title: String, val text: String) : BuilderAnswer
}

/**
 * The clicks and drags of the builder's screens and the Idle tab (flow builder v2, S06), over the flow saved in the
 * player's state and the [Autopilot] that runs it; a flow has room for [slots] steps. A step is changed on its
 * configure screen as a [StepDraft], which joins the flow on Save. While the flow runs nothing is changed (Maxime,
 * 2026-10-09): a configure screen opens to look. The tab's [savedFlows] open the builder (Maxime, 2026-10-10): Load on
 * the current flow's slot opens it as it is, Load on another slot and New replace it, stopping it first; the builder's
 * Save saves it into its slot. Messages go to the chat box.
 */
class BuilderScreen<P : AutopilotPlayer>(
    private val autopilot: Autopilot<P>,
    private val resolver: FlowResolver,
    private val reflexes: ReflexResolver,
    private val names: GameNames,
    private val slots: Int,
    private val savedFlows: SavedFlows,
) {

    private val types = resolver.types.all

    /** A click on the builder's screens; [draft] is the step being configured, null when none is. */
    fun click(player: P, widgetId: Int, draft: StepDraft?): BuilderAnswer =
        when (widgetId) {
            BuilderWidgets.CLOSE -> BuilderAnswer.Close
            BuilderWidgets.RUN -> run(player)
            BuilderWidgets.STOP -> stop(player)
            BuilderWidgets.CLEAR -> clear(player)
            BuilderWidgets.SAVE_FLOW -> saveFromBuilder(player)
            BuilderWidgets.BASE_LEVELS -> levels(player, boosted = false)
            BuilderWidgets.BOOSTED_LEVELS -> levels(player, boosted = true)
            BuilderWidgets.KINDS_BACK -> BuilderAnswer.Show(BuilderPage.OVERVIEW)
            else -> BuilderWidgets.slotOf(widgetId)?.let { slot(player, it) }
                ?: BuilderWidgets.kindOf(widgetId)?.let { kind(player, it) }
                ?: draft?.let { configure(player, widgetId, it) }
                ?: BuilderAnswer.Ignored
        }

    /** A click on the Idle tab: Run, Stop and the saved flows' Load, New and x. */
    fun tab(player: P, widgetId: Int): BuilderAnswer =
        when (widgetId) {
            FlowWidgets.TAB_RUN -> run(player)
            FlowWidgets.TAB_STOP -> stop(player)
            else -> FlowWidgets.savedFlowClick(widgetId)?.let { savedFlow(player, it) } ?: BuilderAnswer.Ignored
        }

    /** [name] entered on the name prompt that Save opened for saved-flow [slot]. */
    fun named(player: P, slot: Int, name: String): BuilderAnswer {
        if (player.idleState.steps.isEmpty()) return say(EMPTY_FLOW)
        val saved = try {
            savedFlows.save(player.idleState, slot, name)
        } catch (e: FlowError) {
            return say("${e.message.take(1).lowercase()}${e.message.drop(1)}.")
        }
        player.idleState = saved
        return say("flow saved as '${name.trim()}'.")
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

    /**
     * A row clicked in the search that stays open, which [opened] asked for its list under [key]: the item [value] names
     * is added, or taken out when the list has it; a full list says so instead (S07a).
     */
    fun toggledItem(draft: StepDraft?, opened: StepDraft, key: String, value: String): BuilderAnswer {
        val current = draft?.takeIf { it.sameStep(opened) } ?: return BuilderAnswer.Ignored
        val id = value.toIntOrNull() ?: return BuilderAnswer.Ignored
        val ids = StepItems.ids(current.settings, key)
        if (id !in ids && ids.size >= StepItems.MOST) return BuilderAnswer.Configure(current, "$PREFIX the list holds ${StepItems.MOST} items.")
        return BuilderAnswer.Configure(current.copy(settings = StepItems.toggled(current.settings, key, id), typing = null))
    }

    /**
     * [value] entered on the "Enter amount" prompt [draft] opened, for a field or a withdrawal's line; out of range it
     * says the rule instead.
     */
    fun typed(draft: StepDraft?, value: Int): BuilderAnswer {
        val typing = draft?.typing ?: return BuilderAnswer.Ignored
        val item = typing.item
        if (item != null) return typedLine(draft, typing.key, item, value)
        val field = fields(draft).filterIsInstance<StepField.Typed>().firstOrNull { it.key == typing.key } ?: return BuilderAnswer.Ignored
        if (value !in field.range) return BuilderAnswer.Configure(draft.notTyping(), "$PREFIX ${field.rule}.")
        return BuilderAnswer.Configure(draft.with(field.key, value.toString()))
    }

    /** A withdrawal takes any count of 1 or more, as every amount does (Maxime, 2026-10-10): no Int is past the top. */
    private fun typedLine(draft: StepDraft, key: String, item: Int, value: Int): BuilderAnswer {
        if (value < StepAmount.RANGE.first) return BuilderAnswer.Configure(draft.notTyping(), "$PREFIX ${StepAmount.RULE}.")
        return BuilderAnswer.Configure(draft.copy(settings = StepItems.withAmount(draft.settings, key, item, value), typing = null))
    }

    /** A tile picked on the world map: it goes into [draft]'s tile, if it has one. */
    fun pickedTile(draft: StepDraft?, tile: Tile): BuilderAnswer {
        val field = draft?.let { fields(it).filterIsInstance<StepField.MapTile>().firstOrNull() } ?: return BuilderAnswer.Ignored
        return BuilderAnswer.Configure(draft.with(field.key, tile.text()))
    }

    /** Load and x act on a slot holding a flow, New on an empty one; the current flow's own slot opens the builder. */
    private fun savedFlow(player: P, click: SavedFlowClick): BuilderAnswer {
        // A slot is never negative, so one comparison says whether the player has it.
        if (click.slot >= savedFlows.slots) return BuilderAnswer.Ignored
        val saved = player.idleState.savedFlows.firstOrNull { it.slot == click.slot }
        val own = player.idleState.savedSlot == click.slot
        return when (click.action) {
            SavedFlowAction.LOAD -> saved?.let { if (own) BuilderAnswer.Open else load(player, click.slot, it.name) } ?: BuilderAnswer.Ignored
            SavedFlowAction.NEW -> if (saved != null) BuilderAnswer.Ignored else if (own) BuilderAnswer.Open else startNew(player, click.slot)
            SavedFlowAction.DELETE -> saved?.let { delete(player, click.slot, it.name) } ?: BuilderAnswer.Ignored
        }
    }

    /**
     * The builder's Save, into the slot the flow belongs to: over its saved flow, starting from its name (Maxime,
     * 2026-10-09), or the new flow's empty slot. A flow belonging to no slot (set through the harness) takes the first
     * empty one; with every slot taken the tab's rows say where (Maxime, 2026-10-10).
     */
    private fun saveFromBuilder(player: P): BuilderAnswer {
        val state = player.idleState
        if (state.steps.isEmpty()) return say(EMPTY_FLOW)
        val slot = state.savedSlot ?: savedFlows.firstEmpty(state)
            ?: return say("every saved-flow slot is taken. Save over one in the Idle tab.")
        val old = savedFlows.current(state)?.name
        return BuilderAnswer.Name(slot, old?.let { "Save over '$it' as:" } ?: "Name for this flow:", old.orEmpty())
    }

    /** Loading replaces the current flow, edits not saved included, and stops it first (Maxime, 2026-10-07 and 10-10). */
    private fun load(player: P, slot: Int, name: String): BuilderAnswer {
        stopForChange(player)
        player.idleState = savedFlows.load(player.idleState, slot)
        return BuilderAnswer.Replaced("$PREFIX loaded '$name'.")
    }

    private fun startNew(player: P, slot: Int): BuilderAnswer {
        stopForChange(player)
        player.idleState = savedFlows.startNew(player.idleState, slot)
        return BuilderAnswer.Replaced()
    }

    /** Emptying the current flow's slot empties the flow too, so it stops first. */
    private fun delete(player: P, slot: Int, name: String): BuilderAnswer {
        if (player.idleState.savedSlot == slot) stopForChange(player)
        player.idleState = savedFlows.empty(player.idleState, slot)
        return say("'$name' deleted.")
    }

    /** A running flow about to be replaced or emptied stops, and the chat box says so before what replaced it. */
    private fun stopForChange(player: P) {
        if (!autopilot.isRunning(player)) return
        autopilot.stop(player)
        player.tell("$PREFIX stopped.")
    }

    private fun run(player: P): BuilderAnswer {
        val steps = player.idleState.steps
        if (steps.isEmpty()) return say(EMPTY_FLOW)
        val cannot = resolver.problems(steps).indexOfFirst { it != null }
        if (cannot >= 0) return BuilderAnswer.Show(message = Autopilot.cannotWork(cannot + 1))
        val reflex = reflexes.problems(steps, player.idleState.reflexes).indexOfFirst { it != null }
        if (reflex >= 0) return say("reflex ${reflex + 1} cannot work yet. See its row in the builder.")
        player.idleState = player.idleState.fromStart()
        autopilot.start(player)
        return say("running from step 1.")
    }

    private fun stop(player: P): BuilderAnswer {
        autopilot.stop(player)
        return say("stopped.")
    }

    /** A cleared flow is a new one: no step and no reflex. */
    private fun clear(player: P): BuilderAnswer {
        if (autopilot.isRunning(player)) return BuilderAnswer.Show(message = STOP_FIRST)
        if (player.idleState.steps.isEmpty() && player.idleState.reflexes.isEmpty()) return BuilderAnswer.Show()
        player.idleState = player.idleState.withFlow(emptyList(), emptyList())
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
        val steps = player.idleState.steps
        return BuilderAnswer.Configure(StepDraft(steps.size, type.newSettings(resolver.contextBefore(steps, steps.size)), new = true))
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
                ?: BuilderWidgets.toggleOf(widgetId)?.let { (row, button) -> if (running) stopFirst else toggle(draft, row, button) }
                ?: BuilderWidgets.listAddOf(widgetId)?.let { list -> if (running) stopFirst else addOrRemove(player, draft, list) }
                ?: BuilderWidgets.lineRemoveOf(widgetId)?.let { (list, line) -> if (running) stopFirst else remove(draft, list, line) }
                ?: BuilderWidgets.lineAmountOf(widgetId)?.let { (list, line) -> if (running) stopFirst else lineAmount(draft, list, line) }
                ?: BuilderWidgets.lineAllOf(widgetId)?.let { (list, line) -> if (running) stopFirst else lineAll(draft, list, line) }
        }
    }

    /** A toggle's button: its choice is kept. */
    private fun toggle(draft: StepDraft, row: Int, button: Int): BuilderAnswer? {
        val toggle = ConfigureRows.of(fields(draft))[row] as? StepField.Toggle ?: return null
        val choice = toggle.choices.getOrNull(button) ?: return null
        return BuilderAnswer.Configure(draft.with(toggle.key, choice.value))
    }

    /** A list's first line opens the search that stays open over its items. */
    private fun addOrRemove(player: P, draft: StepDraft, list: Int): BuilderAnswer? =
        ConfigureRows.list(fields(draft), list)?.let { BuilderAnswer.Several(draft.notTyping(), it, searchContext(player, draft)) }

    /** A list line's x takes its item out. */
    private fun remove(draft: StepDraft, list: Int, line: Int): BuilderAnswer? {
        val (field, id) = lineItem(draft, list, line) ?: return null
        return BuilderAnswer.Configure(draft.copy(settings = StepItems.toggled(draft.settings, field.key, id), typing = null))
    }

    /** A withdrawal's amount box opens "Enter amount" for its line. */
    private fun lineAmount(draft: StepDraft, list: Int, line: Int): BuilderAnswer? {
        val (field, id) = lineItem(draft, list, line)?.takeIf { (field, _) -> field.amounts } ?: return null
        return BuilderAnswer.Amount(draft.copy(typing = Typing(field.key, id)))
    }

    /** A withdrawal's All button: as many as fit (Maxime, 2026-10-10). */
    private fun lineAll(draft: StepDraft, list: Int, line: Int): BuilderAnswer? {
        val (field, id) = lineItem(draft, list, line)?.takeIf { (field, _) -> field.amounts } ?: return null
        return BuilderAnswer.Configure(draft.copy(settings = StepItems.withAmount(draft.settings, field.key, id, amount = null), typing = null))
    }

    /** The list of configure list [list] and the item on its [line], null when there is none. */
    private fun lineItem(draft: StepDraft, list: Int, line: Int): Pair<StepField.Items, Int>? {
        val field = ConfigureRows.list(fields(draft), list) ?: return null
        return StepItems.ids(draft.settings, field.key).getOrNull(line)?.let { field to it }
    }

    private fun save(player: P, draft: StepDraft): BuilderAnswer {
        val steps = player.idleState.steps
        // A draft's slot is never negative, so one comparison says whether the step is still there.
        val replacing = !draft.new && draft.slot < steps.size
        if (!replacing && steps.size >= slots) return BuilderAnswer.Configure(draft.notTyping(), "$PREFIX the flow has room for $slots steps.")
        val slot = if (replacing) draft.slot else steps.size
        val saved = if (replacing) steps.toMutableList().apply { set(slot, draft.settings) } else steps + draft.settings
        player.idleState = player.idleState.withFlow(FlowIds.steps(saved))
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
            is StepField.Note, is StepField.Toggle, is StepField.Items, null -> null
            is StepField.Search -> BuilderAnswer.Search(idle, field, searchContext(player, draft))
            is StepField.Typed -> BuilderAnswer.Amount(draft.copy(typing = Typing(field.key)))
            is StepField.MapTile -> BuilderAnswer.PickTile(idle, tileOf(draft.settings[field.key]) ?: player.tile)
        }
    }

    /** The button that removes a typed amount, so the step does as much as it can. */
    private fun unbounded(draft: StepDraft, row: Int): BuilderAnswer? =
        (ConfigureRows.of(fields(draft))[row] as? StepField.Typed)?.let { BuilderAnswer.Configure(draft.with(it.key, "")) }

    /**
     * The search's options are worked out from the steps before the one configured and its settings as edited, a
     * processing step's from where its input comes from (S07a).
     */
    private fun searchContext(player: P, draft: StepDraft): OptionContext {
        val before = resolver.contextBefore(player.idleState.steps, draft.slot)
        return OptionContext(settings = draft.settings, before = before, input = resolver.types.input(draft.settings, before))
    }

    /** The fields the configure screen shows for [draft]'s settings, as [BuilderConfigure] lays them out. */
    private fun fields(draft: StepDraft): List<StepField> =
        ConfigureRows.shown(resolver.types.find(draft.settings.kind)?.fields(names).orEmpty(), draft.settings)

    /** The tile a map field holds ("x y"), null when it holds none. */
    private fun tileOf(text: String?): Tile? =
        text?.split(" ")?.mapNotNull(String::toIntOrNull)?.takeIf { it.size >= 2 }?.let { (x, y) -> Tile(x, y) }

    private fun say(message: String): BuilderAnswer = BuilderAnswer.Show(message = "$PREFIX $message")

    companion object {
        const val PREFIX = "Autopilot:"
        const val STOP_FIRST = "$PREFIX stop the flow before editing it."
        private const val EMPTY_FLOW = "the flow is empty. Add a step first."
    }
}
