package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FieldColumn
import game.idle.flow.FlowContext
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
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

    /** The row of each of [fields]: the left column's first, then the right's (`BuilderWidgets.row`). */
    fun of(fields: List<StepField>): Map<Int, StepField> {
        val left = fields.filter { it.column == FieldColumn.LEFT }
        val right = fields.filter { it.column == FieldColumn.RIGHT }
        require(left.size <= BuilderWidgets.ROWS_PER_COLUMN && right.size <= BuilderWidgets.ROWS_PER_COLUMN) {
            "The configure screen has ${BuilderWidgets.ROWS_PER_COLUMN} rows a column"
        }
        return left.withIndex().associate { (row, field) -> row to field } +
            right.withIndex().associate { (row, field) -> BuilderWidgets.ROWS_PER_COLUMN + row to field }
    }
}

/**
 * What a step's configure screen shows (flow builder v2, S06b, the mockup's screen): the step's pictures, number, kind,
 * the level of its skill as the player's [OptionFacts] count it and what it does; its settings on rows, the one being
 * typed framed yellow; the reason it cannot work, checked on the settings as edited (the running step's block first),
 * else "No warnings." (Maxime, 2026-10-09); then Delete, Back and Save, greyed when they cannot be used.
 */
class BuilderConfigure(private val resolver: FlowResolver, private val names: GameNames, private val font: ClientFont) {

    private val types = resolver.types

    fun updates(state: IdleState, draft: StepDraft, facts: OptionFacts): List<WidgetUpdate> {
        val settings = draft.settings
        val type = types.find(settings.kind)
        val before = resolver.contextBefore(state.steps, draft.slot)
        val rows = ConfigureRows.of(type?.fields(names).orEmpty())
        val icon = type?.let { WidgetPicture.of(it.icon(settings)) } ?: WidgetPicture.None
        val pictures = StepPictures.of(icon, type?.target(names)?.picked(settings))
        val level = type?.skill(settings)?.let { " @gry@(${Skill.getName(it)} ${facts.level(it)})" }.orEmpty()
        return listOf(
            WidgetUpdate.Picture(BuilderWidgets.HEADER_PICTURE, pictures.picture),
            WidgetUpdate.Picture(BuilderWidgets.HEADER_CORNER, pictures.corner),
            WidgetUpdate.Visible(BuilderWidgets.HEADER_CORNER_LAYER, visible = pictures.corner != WidgetPicture.None),
            WidgetUpdate.Text(BuilderWidgets.HEADER_NAME, "Step ${draft.slot + 1}: ${capitalised(type?.label ?: settings.kind)}$level"),
            WidgetUpdate.Text(BuilderWidgets.HEADER_DESCRIPTION, font.fit(type?.description.orEmpty(), BuilderWidgets.DESCRIPTION_ROOM)),
        ) + (0 until BuilderWidgets.ROWS).flatMap { row -> row(row, rows[row], draft, before) } +
            warnings(state, draft) + buttons(state, draft)
    }

    private fun row(row: Int, field: StepField?, draft: StepDraft, before: FlowContext): List<WidgetUpdate> {
        val shown = field?.let { shown(it, draft.settings, before) } ?: Shown()
        val typed = field as? StepField.Typed
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.row(row), visible = field != null),
            WidgetUpdate.Text(BuilderWidgets.rowLabel(row), field?.label.orEmpty()),
            WidgetUpdate.Visible(BuilderWidgets.rowField(row), visible = field !is StepField.Note),
            WidgetUpdate.Colour(BuilderWidgets.rowFrame(row), if (typed != null && typed.key == draft.typing) BuilderWidgets.TYPING else BuilderWidgets.FIELD_EDGE),
            WidgetUpdate.Picture(BuilderWidgets.rowPicture(row), shown.picture),
            WidgetUpdate.Text(BuilderWidgets.rowText(row), shown.text),
            WidgetUpdate.Text(BuilderWidgets.rowPlainText(row), shown.plainText),
            WidgetUpdate.Visible(BuilderWidgets.rowButton(row), visible = typed?.unbounded != null),
            WidgetUpdate.Text(BuilderWidgets.rowButtonText(row), typed?.unbounded.orEmpty()),
            WidgetUpdate.Text(BuilderWidgets.rowNote(row), shown.note),
        )
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
    }
}
