package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FieldColumn
import game.idle.flow.FlowContext
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexForm
import game.idle.flow.ReflexResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepItem
import game.idle.flow.StepItems
import game.idle.flow.StepSettings
import game.idle.flow.StepWarnings
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionFacts
import io.luna.game.model.mob.Skill

/**
 * What the "Enter amount" prompt is open for: the setting under [key], or, with [item], that item's line in the list
 * under [key] (a withdrawal's amount, S07b).
 */
data class Typing(val key: String, val item: Int? = null)

/** What a configure screen edits: a step, or a reflex (S07c), which its screen edits as settings like a step's. */
enum class DraftSubject { STEP, REFLEX }

/** The page a step's configure screen shows (S07c2b): its settings, or the reflexes attached to it. */
enum class StepTab { SETTINGS, REFLEXES }

/**
 * The step a player configures: the one in flow slot [slot], or a [new] one to go there, with its [settings] as edited,
 * the reflexes attached to it included; they reach the flow only on Save (Maxime, 2026-10-09). [typing] is what the
 * "Enter amount" prompt is open for, its field framed yellow; [tab] the page shown. A [subject] of
 * [DraftSubject.REFLEX] is the flow's reflex [slot] instead (`ReflexForm.settings`), whose screen has no tabs.
 */
data class StepDraft(
    val slot: Int,
    val settings: StepSettings,
    val new: Boolean,
    val typing: Typing? = null,
    val subject: DraftSubject = DraftSubject.STEP,
    val tab: StepTab = StepTab.SETTINGS,
) {

    /** The draft with [key] set to [value] ("" for none) and nothing being typed. */
    fun with(key: String, value: String): StepDraft = copy(settings = settings.with(key, value), typing = null)

    /** The draft once a click closed the client's "Enter amount" prompt. */
    fun notTyping(): StepDraft = copy(typing = null)

    /** Whether [other] configures the same step or reflex, whatever was edited or shown since. */
    fun sameStep(other: StepDraft): Boolean =
        slot == other.slot && new == other.new && settings.kind == other.settings.kind && subject == other.subject

    /** The draft with its attached reflexes now [reflexes] (ids, in the step's order) and nothing being typed. */
    fun attaching(reflexes: List<Int>): StepDraft = copy(settings = settings.copy(reflexes = reflexes), typing = null)

    /**
     * Where the reflexes attached to this step stand among the flow's [reflexes], in the step's order; an id the flow
     * does not hold is left out.
     */
    fun attached(reflexes: List<ReflexSettings>): List<Int> = settings.reflexes.map { id -> reflexes.indexOfFirst { it.id == id } }.filter { it >= 0 }

    /** [steps] with this step in its place, replacing the one it edits or added after them, and its index there. */
    fun inFlow(steps: List<StepSettings>): Pair<List<StepSettings>, Int> =
        if (!new && slot < steps.size) steps.toMutableList().apply { set(slot, settings) } to slot else steps + settings to steps.size
}

/** Where a kind's settings go on the configure screen: each column's fields on its rows, in order. */
object ConfigureRows {

    /** The fields of [fields] that [settings] show (S07b: Deposit's Chosen list only on Chosen). */
    fun shown(fields: List<StepField>, settings: StepSettings): List<StepField> = fields.filter { it.visible(settings) }

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
 * typed framed yellow, a choice as a row of buttons and several items as a list (S07a), a withdrawal's with its amount
 * (S07b); the reason it cannot work, checked on the settings as edited (the running step's block first), then the
 * warnings that never stop it in yellow (S07b), else "No warnings." (Maxime, 2026-10-09); then Delete, Back and Save,
 * greyed when they cannot be used. A step's screen has two tabs (S07c2b): Settings, those rows, and Reflexes, the
 * reflexes attached to it as numbered sentences, red when they cannot work, with the line that attaches or detaches
 * them and Attach all; the warnings band and buttons stay under both. A reflex's screen (S07c) is the same screen over
 * [reflexes], without tabs: its picture, number and what it does, its rows, the reason it cannot work and its food
 * warning.
 */
class BuilderConfigure(
    private val resolver: FlowResolver,
    private val reflexes: ReflexResolver,
    private val form: ReflexForm,
    private val names: GameNames,
    private val font: ClientFont,
) {

    private val types = resolver.types
    private val stepWarnings = StepWarnings(resolver, names)

    fun updates(state: IdleState, draft: StepDraft, facts: OptionFacts): List<WidgetUpdate> {
        val screen = if (draft.subject == DraftSubject.REFLEX) reflexScreen(state, draft, facts) else stepScreen(state, draft, facts)
        val reflexPage = draft.subject == DraftSubject.STEP && draft.tab == StepTab.REFLEXES
        val fields = if (reflexPage) emptyList() else ConfigureRows.shown(screen.fields, draft.settings)
        val rows = ConfigureRows.of(fields)
        return listOf(
            WidgetUpdate.Picture(BuilderWidgets.HEADER_PICTURE, screen.pictures.picture),
            WidgetUpdate.Picture(BuilderWidgets.HEADER_CORNER, screen.pictures.corner),
            WidgetUpdate.Visible(BuilderWidgets.HEADER_CORNER_LAYER, visible = screen.pictures.corner != WidgetPicture.None),
            WidgetUpdate.Text(BuilderWidgets.HEADER_NAME, screen.name),
            WidgetUpdate.Text(BuilderWidgets.HEADER_DESCRIPTION, font.fit(screen.description, BuilderWidgets.DESCRIPTION_ROOM)),
        ) + (0 until BuilderWidgets.ROWS).flatMap { row -> row(row, rows[row], draft, screen.before) } +
            (0 until BuilderWidgets.LISTS).flatMap { list(it, rows, ConfigureRows.list(fields, it), draft) } +
            tabs(draft) + attached(state, draft, reflexPage) + warnings(screen.reason, screen.warnings) + buttons(state, draft)
    }

    /** A step's Settings and Reflexes tabs, the one shown lit as the overview's tabs are; a reflex's screen has none. */
    private fun tabs(draft: StepDraft): List<WidgetUpdate> {
        if (draft.subject == DraftSubject.REFLEX) return listOf(WidgetUpdate.Visible(BuilderWidgets.STEP_TABS, visible = false))
        val reflexPage = draft.tab == StepTab.REFLEXES
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.STEP_TABS, visible = true),
            WidgetUpdate.Text(BuilderWidgets.SETTINGS_TAB, lit("Settings", !reflexPage)),
            WidgetUpdate.Colour(BuilderWidgets.SETTINGS_TAB_FRAME, tabFrame(lit = !reflexPage)),
            WidgetUpdate.Text(BuilderWidgets.STEP_REFLEXES_TAB, lit("Reflexes", reflexPage)),
            WidgetUpdate.Colour(BuilderWidgets.STEP_REFLEXES_TAB_FRAME, tabFrame(lit = reflexPage)),
        )
    }

    /**
     * The Reflexes page, shown on its tab: placed over the rows' area, sized to its lines; the line that attaches or
     * detaches and Attach all, greyed while the flow holds no reflex or runs; then each attached reflex's number in the
     * flow, picture and sentence, red when it cannot work, as the overview's rows show it.
     */
    private fun attached(state: IdleState, draft: StepDraft, shown: Boolean): List<WidgetUpdate> {
        if (!shown) return listOf(WidgetUpdate.Visible(BuilderWidgets.ATTACHED, visible = false))
        val attached = draft.attached(state.reflexes)
        val problems = reflexes.problems(state.steps, state.reflexes)
        val usable = state.reflexes.isNotEmpty() && !state.running
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.ATTACHED, visible = true),
            WidgetUpdate.Placement(BuilderWidgets.ATTACHED_LIST, firstRow = 0, rows = BuilderWidgets.ROWS_PER_COLUMN, lines = attached.size + 1),
            WidgetUpdate.Text(BuilderWidgets.ATTACH_TEXT, usable(ATTACH, usable)),
            WidgetUpdate.Text(BuilderWidgets.ATTACH_ALL_TEXT, usable(ATTACH_ALL, usable)),
        ) + attached.withIndex().flatMap { (line, index) ->
            val reflex = state.reflexes[index]
            val sentence = font.fit(form.sentence(reflex, state.steps), BuilderWidgets.SENTENCE_ROOM)
            listOf(
                WidgetUpdate.Text(BuilderWidgets.attachedNumber(line), "${index + 1}"),
                WidgetUpdate.Picture(BuilderWidgets.attachedPicture(line), WidgetPicture.of(form.icon(reflex))),
                WidgetUpdate.Text(BuilderWidgets.attachedSentence(line), if (problems[index] != null) "@red@$sentence" else sentence),
            )
        }
    }

    /** What sets a step's screen apart from a reflex's: its header, fields, what the steps before set up and its warnings. */
    private data class Screen(
        val pictures: StepPictures,
        val name: String,
        val description: String,
        val fields: List<StepField>,
        val before: FlowContext,
        val reason: String?,
        val warnings: List<String>,
    )

    private fun stepScreen(state: IdleState, draft: StepDraft, facts: OptionFacts): Screen {
        val settings = draft.settings
        val type = types.find(settings.kind)
        val (flow, index) = draft.inFlow(state.steps)
        val icon = type?.let { WidgetPicture.of(it.icon(settings)) } ?: WidgetPicture.None
        val level = type?.skill(settings)?.let { " @gry@(${Skill.getName(it)} ${facts.level(it)})" }.orEmpty()
        val running = state.running && !draft.new && state.stepIndex == draft.slot
        val block = state.blocked?.takeIf { running }
        val waiting = state.waiting?.takeIf { running }
        return Screen(
            pictures = StepPictures.of(icon, type?.pick(settings, names)?.icon),
            name = "Step ${draft.slot + 1}: ${capitalised(type?.label ?: settings.kind)}$level",
            description = type?.description.orEmpty(),
            fields = type?.fields(names).orEmpty(),
            before = resolver.contextBefore(flow, index),
            reason = block ?: resolver.problems(state.steps.take(draft.slot) + settings).last(),
            warnings = listOfNotNull(waiting) + stepWarnings.of(flow, index, facts, state.reflexes),
        )
    }

    private fun reflexScreen(state: IdleState, draft: StepDraft, facts: OptionFacts): Screen {
        val reflex = form.reflex(draft.settings)
        val replacing = !draft.new && draft.slot < state.reflexes.size
        val index = if (replacing) draft.slot else state.reflexes.size
        val inFlow = if (replacing) state.reflexes.toMutableList().apply { set(index, reflex) } else state.reflexes + reflex
        return Screen(
            pictures = StepPictures(WidgetPicture.of(form.icon(reflex)), WidgetPicture.None),
            name = "Reflex ${draft.slot + 1}: ${form.label(reflex)}",
            description = form.description(reflex),
            fields = form.fields(state.steps),
            before = FlowContext(),
            reason = reflexes.problems(state.steps, inFlow)[index],
            warnings = listOfNotNull(form.need(reflex)?.let { stepWarnings.supplies(state.steps, it, facts) }),
        )
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
        val button = button(field, draft.settings)
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.row(row), visible = true),
            WidgetUpdate.Text(BuilderWidgets.rowLabel(row), field.label),
            WidgetUpdate.Visible(BuilderWidgets.rowField(row), visible = boxed),
            WidgetUpdate.Visible(BuilderWidgets.rowButton(row), visible = button != null),
            WidgetUpdate.Text(BuilderWidgets.rowNote(row), shown.note),
        ) + box(row, shown, typed, draft).takeIf { boxed }.orEmpty() +
            listOfNotNull(button?.let { WidgetUpdate.Text(BuilderWidgets.rowButtonText(row), it) }) +
            toggles(row, field as? StepField.Toggle, draft.settings, before)
    }

    /**
     * The word on a row's button, null for none: a typed amount's that removes it, a map tile's x while it holds a tile
     * it may go without (S07e's Work spot, back to From the flow).
     */
    private fun button(field: StepField, settings: StepSettings): String? =
        when (field) {
            is StepField.Typed -> field.unbounded
            is StepField.MapTile -> CLEAR.takeIf { field.none != null && settings[field.key] != null }
            else -> null
        }

    /** A field box: its frame (yellow while typed), picture and text. */
    private fun box(row: Int, shown: Shown, typed: StepField.Typed?, draft: StepDraft): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Colour(BuilderWidgets.rowFrame(row), frame(typed != null && Typing(typed.key) == draft.typing)),
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
     * remove, then a line per item: its picture and its name, and for a withdrawal its amount. Lines past the last lie
     * outside the list (the client sizes it to its lines), so they need no update.
     */
    private fun list(list: Int, rows: Map<Int, StepField>, field: StepField.Items?, draft: StepDraft): List<WidgetUpdate> {
        if (field == null) return listOf(WidgetUpdate.Visible(BuilderWidgets.list(list), visible = false))
        val firstRow = rows.entries.first { it.value === field }.key % BuilderWidgets.ROWS_PER_COLUMN
        val items = StepItems.read(draft.settings, field.key).take(BuilderWidgets.LIST_LINES)
        val nameRoom = if (field.amounts) BuilderWidgets.LINE_NAME_ROOM_AMOUNT else BuilderWidgets.LINE_NAME_ROOM
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.list(list), visible = true),
            WidgetUpdate.Placement(list, firstRow, field.rows, lines = items.size + 1),
            WidgetUpdate.Text(BuilderWidgets.listAddText(list), field.empty?.takeIf { items.isEmpty() } ?: field.add),
        ) + items.withIndex().flatMap { (line, item) ->
            listOf(
                WidgetUpdate.Picture(BuilderWidgets.linePicture(list, line), WidgetPicture.Item(item.id)),
                WidgetUpdate.Text(BuilderWidgets.lineName(list, line), font.fit(names.item(item.id), nameRoom)),
                WidgetUpdate.Visible(BuilderWidgets.lineAmount(list, line), visible = field.amounts),
            ) + if (field.amounts) amount(list, line, field.key, item, draft) else emptyList()
        }
    }

    /** A withdrawal's amount box: its count as a stack shows it, or All, framed yellow while typed. */
    private fun amount(list: Int, line: Int, key: String, item: StepItem, draft: StepDraft): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Text(BuilderWidgets.lineAmountText(list, line), item.amount?.let(StepAmount::short) ?: ALL),
            WidgetUpdate.Colour(BuilderWidgets.lineAmountFrame(list, line), frame(Typing(key, item.id) == draft.typing)),
        )

    private fun frame(typing: Boolean): Int = if (typing) BuilderWidgets.TYPING else BuilderWidgets.FIELD_EDGE

    /** A row's contents: a search's picture and text, a typed number's or a tile's text (or the word for none), or a note. */
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
                val text = picked?.label ?: settings[field.target.key]?.let { field.missing ?: it }
                Shown(picked?.let { WidgetPicture.of(it.icon) } ?: WidgetPicture.None, text?.let(::fittedField) ?: SEARCH)
            }
            is StepField.Typed -> Shown(plainText = field.shown(settings[field.key]))
            is StepField.MapTile -> Shown(plainText = settings[field.key]?.replace(" ", ", ") ?: field.none ?: PICK_ON_MAP)
            is StepField.Note -> Shown(note = font.fit(field.text(settings, before), BuilderWidgets.NOTE_ROOM))
        }

    /** The red [reason] first, then each yellow warning on its lines, as many lines as the band has. */
    private fun warnings(reason: String?, warnings: List<String>): List<WidgetUpdate> {
        val red = listOfNotNull(reason).flatMap { warning(it, "@red@") }
        val yellow = warnings.flatMap { warning(it, "@yel@") }
        val lines = (red + yellow).ifEmpty { listOf(NO_WARNINGS) }
        return (0 until BuilderWidgets.WARNING_LINES).map { WidgetUpdate.Text(BuilderWidgets.warning(it), lines.getOrNull(it).orEmpty()) }
    }

    private fun warning(text: String, colour: String): List<String> =
        font.wrap("! $text", BuilderWidgets.WARNING_ROOM, BuilderWidgets.WARNING_LINES).map { "$colour$it" }

    private fun buttons(state: IdleState, draft: StepDraft): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Text(BuilderWidgets.DELETE, usable("Delete", !draft.new && !state.running)),
            WidgetUpdate.Text(BuilderWidgets.BACK, "Back"),
            WidgetUpdate.Text(BuilderWidgets.SAVE, usable("Save", !state.running)),
        )

    private fun fittedField(text: String): String = font.fit(text, BuilderWidgets.FIELD_ROOM)

    private fun lit(text: String, on: Boolean): String = if (on) "@whi@$text" else text

    private fun tabFrame(lit: Boolean): Int = if (lit) BuilderWidgets.LIT else BuilderWidgets.UNLIT

    private fun usable(text: String, can: Boolean): String = if (can) text else "@gry@$text"

    private companion object {
        const val SEARCH = "@gry@Search..."
        const val PICK_ON_MAP = "@gry@Pick on the world map"
        const val CLEAR = "x"
        const val NO_WARNINGS = "@gre@No warnings."
        const val ALL = "All"
        const val ATTACH = "+ Attach or detach reflexes..."
        const val ATTACH_ALL = "Attach all"

        /** A toggle shows two or three buttons. */
        val TOGGLE_SIZES = listOf(2, 3)
    }
}
