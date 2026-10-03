package game.idle.ui

import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotPlayer
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
import game.idle.location.Tile

/** What the game has to do after a click, besides showing the new texts. */
sealed interface ClickResult {
    data object Ignored : ClickResult
    data object Open : ClickResult
    data object Close : ClickResult
    data object Refresh : ClickResult

    /** Open the world map over the builder for the player to pick a tile, centred on [centre]. */
    data class PickTile(val centre: Tile) : ClickResult
}

/**
 * The flow builder behind the IdleRS widgets: keeps each player's draft step and last message, edits the saved
 * flow, and runs or stops it through the [Autopilot]. Every edit is validated with the same [FlowResolver] as
 * `::flow add`, so the builder cannot save a flow the commands would refuse.
 */
class FlowBuilder<P : AutopilotPlayer>(private val autopilot: Autopilot<P>, private val resolver: FlowResolver) {

    private val types = resolver.grammar.types

    init {
        require(types.all { it.fields.size <= FlowWidgets.DRAFT_FIELDS.size }) {
            "The builder shows ${FlowWidgets.DRAFT_FIELDS.size} fields per step at most"
        }
    }

    private val drafts = mutableMapOf<String, FlowDraft>()
    private val messages = mutableMapOf<String, String>()
    private val editing = mutableMapOf<String, Int>()
    private val picking = mutableMapOf<String, Int>()

    /** Tile fields the draft leaves blank show the player's own tile. */
    fun draft(player: P): FlowDraft =
        drafts.getOrPut(player.username) { FlowDraft.first(types) }.withBlankTiles(player.tile.text())

    fun message(player: P): String = messages[player.username] ?: ""

    /** The row loaded into the draft by a click on it, null while the draft is a new step. */
    fun editing(player: P): Int? = editing[player.username]

    fun texts(player: P): Map<Int, String> =
        BuilderView.texts(player.idleState, draft(player), message(player), editing(player))

    fun forget(player: P) {
        drafts.remove(player.username)
        messages.remove(player.username)
        editing.remove(player.username)
        picking.remove(player.username)
    }

    /**
     * A tile picked on the world map: it goes into the tile field the map was opened for, as long as the draft still
     * has that field (the map takes every click while open, so normally it does).
     */
    fun picked(player: P, tile: Tile): ClickResult {
        val index = picking.remove(player.username) ?: return ClickResult.Ignored
        val draft = draft(player)
        if (draft.type.fields.getOrNull(index) !is StepField.MapTile) return ClickResult.Ignored
        drafts[player.username] = draft.withValue(index, tile.text())
        return say(player, "")
    }

    fun click(player: P, widgetId: Int): ClickResult =
        when (val action = FlowWidgets.action(widgetId)) {
            null -> ClickResult.Ignored
            BuilderAction.OpenBuilder -> opened(player)
            BuilderAction.Close -> ClickResult.Close
            is BuilderAction.EditRow -> load(player, action.row)
            BuilderAction.NewStep -> newStep(player)
            is BuilderAction.MoveUp -> edit(player) { moved(it, action.row, action.row - 1) }
            is BuilderAction.MoveDown -> edit(player) { moved(it, action.row, action.row + 1) }
            is BuilderAction.Delete -> edit(player) { lines -> lines.filterIndexed { row, _ -> row != action.row } }
            BuilderAction.CycleKind -> redraft(player) { it.nextType(types) }
            is BuilderAction.CycleField -> field(player, action.index)
            BuilderAction.Add -> add(player)
            BuilderAction.Run -> run(player)
            BuilderAction.Stop -> stop(player)
            BuilderAction.Clear -> clear(player)
        }

    private fun opened(player: P): ClickResult {
        say(player, "")
        return ClickResult.Open
    }

    /** A choice moves to the next one; a tile opens the world map, centred on the tile the field holds. */
    private fun field(player: P, index: Int): ClickResult {
        val draft = draft(player)
        return when (draft.type.fields.getOrNull(index)) {
            null, is StepField.Choice -> redraft(player) { it.nextValue(index) }
            is StepField.MapTile -> pickTile(player, index, draft.values[index])
        }
    }

    private fun pickTile(player: P, index: Int, value: String): ClickResult {
        picking[player.username] = index
        val (x, y) = value.split(" ").map { it.toInt() }
        return ClickResult.PickTile(Tile(x, y))
    }

    private fun redraft(player: P, change: (FlowDraft) -> FlowDraft): ClickResult {
        drafts[player.username] = change(draft(player))
        say(player, "")
        return ClickResult.Refresh
    }

    /** Loads the step on [row] into the draft; saving then replaces that row. */
    private fun load(player: P, row: Int): ClickResult {
        val line = player.idleState.flow.getOrNull(row) ?: return say(player, "")
        val step = try {
            resolver.grammar.parse(line)
        } catch (e: FlowError) {
            return say(player, e.message)
        }
        drafts[player.username] = draft(player).editing(step)
        editing[player.username] = row
        return say(player, "Editing step ${row + 1}. Change the fields, then save.")
    }

    private fun newStep(player: P): ClickResult {
        editing.remove(player.username)
        return say(player, "")
    }

    /** Moving or deleting rows renumbers them, so an edit in progress is dropped. */
    private fun edit(player: P, change: (List<String>) -> List<String>): ClickResult {
        if (autopilot.isRunning(player)) return say(player, "Stop the flow before editing it.")
        editing.remove(player.username)
        val lines = change(player.idleState.flow)
        if (lines != player.idleState.flow) player.idleState = player.idleState.withFlow(lines)
        return say(player, problem(lines) ?: "")
    }

    private fun moved(lines: List<String>, from: Int, to: Int): List<String> {
        val moving = lines.getOrNull(from) ?: return lines
        val other = lines.getOrNull(to) ?: return lines
        return lines.toMutableList().also { it[from] = other; it[to] = moving }
    }

    /** Appends the draft, or replaces the row being edited (appends when that row is gone). */
    private fun add(player: P): ClickResult {
        if (autopilot.isRunning(player)) return say(player, "Stop the flow before editing it.")
        val flow = player.idleState.flow
        val row = editing[player.username]?.takeIf { it < flow.size }
        if (row == null && flow.size >= FlowWidgets.ROWS) return say(player, "The flow is full (${FlowWidgets.ROWS} steps).")
        val line = draft(player).line()
        val lines = if (row == null) flow + line else flow.toMutableList().also { it[row] = line }
        problem(lines)?.let { return say(player, it) }
        player.idleState = player.idleState.withFlow(lines)
        editing.remove(player.username)
        val verb = if (row == null) "added" else "saved"
        return say(player, "Step ${(row ?: lines.lastIndex) + 1} $verb: $line")
    }

    private fun run(player: P): ClickResult {
        val flow = player.idleState.flow
        if (flow.isEmpty()) return say(player, "The flow is empty. Add a step first.")
        problem(flow)?.let { return say(player, it) }
        player.idleState = player.idleState.fromStart()
        autopilot.start(player)
        return say(player, "Running step 1: ${flow[0]}")
    }

    private fun stop(player: P): ClickResult {
        autopilot.stop(player)
        return say(player, "Stopped.")
    }

    private fun clear(player: P): ClickResult {
        autopilot.stop(player)
        editing.remove(player.username)
        player.idleState = player.idleState.withFlow(emptyList())
        return say(player, "Flow cleared.")
    }

    /** The resolver's complaint about [lines], or null when they make a runnable flow. */
    private fun problem(lines: List<String>): String? =
        try {
            resolver.resolve(lines)
            null
        } catch (e: FlowError) {
            e.message
        }

    private fun say(player: P, message: String): ClickResult {
        messages[player.username] = message
        return ClickResult.Refresh
    }
}
