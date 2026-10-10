package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FieldColumn
import game.idle.flow.FlowContext
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
import game.idle.flow.StepItems
import game.idle.flow.StepSettings
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionFacts
import io.luna.game.model.mob.Skill

/**
 * The step a player configures: the one in flow slot [slot], or a [new] one to go there, with its [settings] as edited;
 * they reach the flow only on Save (Maxime, 2026-10-09). [typing] is the key of the setting the "Enter amount" prompt
 * is open for, its field framed yellow.
 */
data class StepDraft(val slot: Int, val settings: StepSettings, val new: Boolean, val typing: String? = null) {

    /** The draft with [key] set to [value] ("" for none) and nothing being typed. */
    fun with(key: String, value: String): StepDraft = copy(settings = settings.with(key, value), typing = null)

    /** The draft once a click closed the client's "Enter amount" prompt. */
    fun notTyping(): StepDraft = copy(typing = null)

    /** Whether [other] configures the same step, whatever was edited since. */
    fun sameStep(other: StepDraft): Boolean = slot == other.slot && new == other.new && settings.kind == other.settings.kind
}

/** Where a kind's settings go on the configure screen: each column's fields on its rows, in order. */
object ConfigureRows {

    /** The first row of each of [fields]: the left column's from 0, the right's from `BuilderWidgets.ROWS_PER_COLUMN`. */
    fun of(fields: List<StepField>): Map<Int, StepField> =
        column(fields, FieldColumn.LEFT, first = 0) + column(fields, FieldColumn.RIGHT, first = BuilderWidgets.ROWS_PER_COLUMN)

    /** The list of [fields] in configure list [list] (0, the left column's, or 1), null when that column has none. */
    fun list(fields: List<StepField>, list: Int): StepField.Items? =
        fields.filterIsInstance<StepField.Items>().firstOrNull { it.column == columnOf(list) }

    fun columnOf(list: Int): FieldColumn = if (list == 0) FieldColumn.LEFT else FieldColumn.RIGHT

    private fun column(fields: List<StepField>, column: FieldColumn, first: Int): Map<Int, StepField> {
        val inColumn = fields.filter { it.column == column }
        require(inColumn.sumOf { it.rows } <= BuilderWidgets.ROWS_PER_COLUMN) {
            "The configure screen has ${BuilderWidgets.ROWS_PER_COLUMN} rows a column"
        }
        require(inColumn.count { it is StepField.Items } <= 1) { "The configure screen has one list a column" }
        val starts = inColumn.runningFold(first) { row, field -> row + field.rows }
        return inColumn.withIndex().associate { (index, field) -> starts[index] to field }
    }
}

/**
 * What a step's configure screen shows (flow builder v2, S06b, the mockup's screen): the step's pictures, number, kind,
 * the level of its skill as the player's [OptionFacts] count it and what it does; its settings on rows, the one being
 * typed framed yellow, a choice as a row of buttons and several items as a list (S07a); the reason it cannot work,
 * checked on the settings as edited (the running step's block first), else "No warnings." (Maxime, 2026-10-09); then
 * Delete, Back and Save, greyed when they cannot be used.
 */
class BuilderConfigure(private val resolver: FlowResolver, private val names: GameNames, private val font: ClientFont) {

    private val types = resolver.types

    fun updates(state: IdleState, draft: StepDraft, facts: OptionFacts): List<WidgetUpdate> {
        val settings = draft.settings
        val type = types.find(settings.kind)
        val before = resolver.contextBefore(state.steps, draft.slot)
        val fields = type?.fields(names).orEmpty()
        val rows = ConfigureRows.of(fields)
        val icon = type?.let { WidgetPicture.of(it.icon(settings)) } ?: WidgetPicture.None
        val pictures = StepPictures.of(icon, type?.pick(settings, names)?.icon)
        val level = type?.skill(settings)?.let { " @gry@(${Skill.getName(it)} ${facts.level(it)})" }.orEmpty()
        return listOf(
            WidgetUpdate.Picture(BuilderWidgets.HEADER_PICTURE, pictures.picture),
            WidgetUpdate.Picture(BuilderWidgets.HEADER_CORNER, pictures.corner),
            WidgetUpdate.Visible(BuilderWidgets.HEADER_CORNER_LAYER, visible = pictures.corner != WidgetPicture.None),
            WidgetUpdate.Text(BuilderWidgets.HEADER_NAME, "Step ${draft.slot + 1}: ${capitalised(type?.label ?: settings.kind)}$level"),
            WidgetUpdate.Text(BuilderWidgets.HEADER_DESCRIPTION, font.fit(type?.description.orEmpty(), BuilderWidgets.DESCRIPTION_ROOM)),
        ) + (0 until BuilderWidgets.ROWS).flatMap { row -> row(row, rows[row], draft, before) } +
            (0 until BuilderWidgets.LISTS).flatMap { list(it, rows, ConfigureRows.list(fields, it), settings) } +
            warnings(state, draft) + buttons(state, draft)
    }

    /**
     * Row [row]: hidden when no field starts on it, its contents then left as they are; else its label, and the widgets
     * of the field's sort shown and filled while the others hide. Only what a screen uses is sent, so an open builder
     * stays a few dozen messages a refresh.
     */
    private fun row(row: Int, field: StepField?, draft: StepDraft, before: FlowContext): List<WidgetUpdate> {
        if (field == null) return listOf(WidgetUpdate.Visible(BuilderWidgets.row(row), visible = false))
        val shown = shown(field, draft.settings, before)
        val typed = field as? StepField.Typed
        val boxed = field is StepField.Search || field is StepField.Typed || field is StepField.MapTile
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.row(row), visible = true),
            WidgetUpdate.Text(BuilderWidgets.rowLabel(row), field.label),
            WidgetUpdate.Visible(BuilderWidgets.rowField(row), visible = boxed),
            WidgetUpdate.Visible(BuilderWidgets.rowButton(row), visible = typed?.unbounded != null),
            WidgetUpdate.Text(BuilderWidgets.rowNote(row), shown.note),
        ) + box(row, shown, typed, draft).takeIf { boxed }.orEmpty() +
            listOfNotNull(typed?.unbounded?.let { WidgetUpdate.Text(BuilderWidgets.rowButtonText(row), it) }) +
            toggles(row, field as? StepField.Toggle, draft.settings, before)
    }

    /** A field box: its frame (yellow while typed), picture and text. */
    private fun box(row: Int, shown: Shown, typed: StepField.Typed?, draft: StepDraft): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Colour(BuilderWidgets.rowFrame(row), if (typed != null && typed.key == draft.typing) BuilderWidgets.TYPING else BuilderWidgets.FIELD_EDGE),
            WidgetUpdate.Picture(BuilderWidgets.rowPicture(row), shown.picture),
            WidgetUpdate.Text(BuilderWidgets.rowText(row), shown.text),
            WidgetUpdate.Text(BuilderWidgets.rowPlainText(row), shown.plainText),
        )

    /** A toggle's buttons, the set of its size shown, the chosen one lit as the overview's Levels toggle is. */
    private fun toggles(row: Int, toggle: StepField.Toggle?, settings: StepSettings, before: FlowContext): List<WidgetUpdate> {
        val current = toggle?.current?.invoke(settings, before)
        return TOGGLE_SIZES.flatMap { count ->
            val shown = toggle?.takeIf { it.choices.size == count }
            listOf(WidgetUpdate.Visible(BuilderWidgets.toggles(row, count), visible = shown != null)) +
                shown?.choices.orEmpty().withIndex().flatMap { (button, choice) ->
                    val lit = choice.value == current
                    listOf(
                        WidgetUpdate.Text(BuilderWidgets.toggleText(row, count, button), if (lit) "@whi@${choice.word}" else choice.word),
                        WidgetUpdate.Colour(BuilderWidgets.toggleFrame(row, count, button), if (lit) BuilderWidgets.LIT else BuilderWidgets.FIELD_EDGE),
                    )
                }
        }
    }

    /**
     * Configure list [list]: hidden without a list in its column, else placed on its field's rows with a line to add or
     * remove, then a line per item: its picture and its name. Lines past the last lie outside the list (the client
     * sizes it to its lines), so they need no update.
     */
    private fun list(list: Int, rows: Map<Int, StepField>, field: StepField.Items?, settings: StepSettings): List<WidgetUpdate> {
        if (field == null) return listOf(WidgetUpdate.Visible(BuilderWidgets.list(list), visible = false))
        val firstRow = rows.entries.first { it.value === field }.key % BuilderWidgets.ROWS_PER_COLUMN
        val ids = StepItems.ids(settings, field.key).take(BuilderWidgets.LIST_LINES)
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.list(list), visible = true),
            WidgetUpdate.Placement(list, firstRow, field.rows, lines = ids.size + 1),
            WidgetUpdate.Text(BuilderWidgets.listAddText(list), field.add),
        ) + ids.withIndex().flatMap { (line, id) ->
            listOf(
                WidgetUpdate.Picture(BuilderWidgets.linePicture(list, line), WidgetPicture.Item(id)),
                WidgetUpdate.Text(BuilderWidgets.lineName(list, line), font.fit(names.item(id), BuilderWidgets.LINE_NAME_ROOM)),
                WidgetUpdate.Visible(BuilderWidgets.lineAmount(list, line), visible = false),
            )
        }
    }

    /** A row's contents: a search's picture and text, a typed number's or a tile's text, or a note. */
    private data class Shown(
        val picture: WidgetPicture = WidgetPicture.None,
        val text: String = "",
        val plainText: String = "",
        val note: String = "",
    )

    private fun shown(field: StepField, settings: StepSettings, before: FlowContext): Shown =
        when (field) {
            // Toggles and lists draw on widgets of their own.
            is StepField.Toggle, is StepField.Items -> Shown()
            is StepField.Search -> {
                val picked = field.target.picked(settings)
                val text = picked?.label ?: settings[field.target.key]
                Shown(picked?.let { WidgetPicture.of(it.icon) } ?: WidgetPicture.None, text?.let(::fittedField) ?: SEARCH)
            }
            is StepField.Typed -> Shown(plainText = field.shown(settings[field.key]))
            is StepField.MapTile -> Shown(plainText = settings[field.key]?.replace(" ", ", ") ?: PICK_ON_MAP)
            is StepField.Note -> Shown(note = font.fit(field.text(settings, before), BuilderWidgets.NOTE_ROOM))
        }

    private fun warnings(state: IdleState, draft: StepDraft): List<WidgetUpdate> {
        val running = state.running && !draft.new && state.stepIndex == draft.slot
        val block = state.blocked?.takeIf { running }?.let(AutopilotStatus::reason)
        val reason = block ?: resolver.problems(state.steps.take(draft.slot) + draft.settings).last()
        val lines = reason?.let { font.wrap("! $it", BuilderWidgets.WARNING_ROOM, BuilderWidgets.WARNING_LINES).map { line -> "@red@$line" } }
            ?: listOf(NO_WARNINGS)
        return (0 until BuilderWidgets.WARNING_LINES).map { WidgetUpdate.Text(BuilderWidgets.warning(it), lines.getOrNull(it).orEmpty()) }
    }

    private fun buttons(state: IdleState, draft: StepDraft): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Text(BuilderWidgets.DELETE, usable("Delete", !draft.new && !state.running)),
            WidgetUpdate.Text(BuilderWidgets.BACK, "Back"),
            WidgetUpdate.Text(BuilderWidgets.SAVE, usable("Save", !state.running)),
        )

    private fun fittedField(text: String): String = font.fit(text, BuilderWidgets.FIELD_ROOM)

    private fun usable(text: String, can: Boolean): String = if (can) text else "@gry@$text"

    private companion object {
        const val SEARCH = "@gry@Search..."
        const val PICK_ON_MAP = "@gry@Pick on the world map"
        const val NO_WARNINGS = "@gre@No warnings."

        /** A toggle shows two or three buttons. */
        val TOGGLE_SIZES = listOf(2, 3)
    }
}
