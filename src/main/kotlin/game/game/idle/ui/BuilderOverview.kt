package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FlowResolver
import game.idle.flow.FlowStops
import game.idle.flow.ReflexForm
import game.idle.flow.ReflexResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.SavedFlows
import game.idle.flow.SlotWords
import game.idle.flow.StepDangers
import game.idle.flow.StepLines
import game.idle.flow.StepSettings
import game.idle.flow.capitalised
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.font.ClientFont

/**
 * The builder's screens the server shows one at a time: the overview on its Steps tab, its Reflexes tab (S07c) or its
 * Travel tab (web-walker movement Part 3b), the kind picker, and a step's or reflex's configure screen.
 */
enum class BuilderPage { OVERVIEW, REFLEXES, TRAVEL, KINDS, CONFIGURE }

/** The pictures a step shows: its target's [picture] with the kind's icon in the [corner], or the kind's icon alone. */
data class StepPictures(val picture: WidgetPicture, val corner: WidgetPicture) {

    companion object {
        /** [icon] is the kind's, [picked] the picture of what the step picked, null for none. */
        fun of(icon: WidgetPicture, picked: OptionIcon?): StepPictures =
            picked?.let { StepPictures(WidgetPicture.of(it), icon) } ?: StepPictures(icon, WidgetPicture.None)
    }
}

/** One step slot of the overview as the player sees it. */
sealed interface SlotView {

    /**
     * A step: its [number], the target's [picture] with the kind's icon in the [corner] (none when the picture is the
     * kind's own), its [kind], the [lines] under it and the colour of its [frame].
     */
    data class Step(
        val number: Int,
        val picture: WidgetPicture,
        val corner: WidgetPicture,
        val kind: String,
        val lines: List<String>,
        val frame: Int,
    ) : SlotView

    /** The first free slot, which adds a step. */
    data object Add : SlotView

    data object Free : SlotView
}

/** One reflex row of the overview's Reflexes tab as the player sees it (S07c). */
sealed interface ReflexRowView {

    /** A reflex: its [number], [picture] and [sentence], framed in [frame]'s colour, red when it cannot work. */
    data class Reflex(val number: Int, val picture: WidgetPicture, val sentence: String, val frame: Int) : ReflexRowView

    /** The first free row, which adds a reflex. */
    data object Add : ReflexRowView

    data object Free : ReflexRowView
}

/**
 * What the builder's overview and kind picker show for a player's [IdleState] (flow builder v2, S06a, the mockup's
 * screens): the title naming the saved flow it holds, of [savedFlows]; a slot per step slot, each step with its target,
 * kind and details, framed green while it runs and red with its reason when it cannot work (Maxime, 2026-10-09: what
 * the resolver refuses, or the running step's block); then the status line, the levels toggle and the buttons. Texts
 * are fitted to the client's small font, [font]; a reason takes the last lines, up to three, the details giving way to
 * it, and what never stops the step follows it in yellow on those lines: the running step's wait, then the aggressive
 * npcs within reach of where the step works ([dangers], S08). The Reflexes tab (S07c) shows a row per reflex slot, each
 * reflex as [form] words it, red when [reflexes] refuses it; the Steps tab's "Attach all reflexes" (S07c2b) is greyed
 * unless a stopped flow holds steps and reflexes. The Travel tab shows what the flow's trips may spend ([TravelPage]).
 */
class BuilderOverview(
    private val resolver: FlowResolver,
    private val reflexes: ReflexResolver,
    private val form: ReflexForm,
    private val names: GameNames,
    private val font: ClientFont,
    private val savedFlows: SavedFlows,
    private val dangers: StepDangers,
) {

    private val types = resolver.types
    private val lines = StepLines(resolver, names)
    private val travel = TravelPage(names, font)

    /** The slots as the player [facts] describe sees them (the dangers depend on their combat level and tile). */
    fun slots(state: IdleState, slots: Int, facts: OptionFacts): List<SlotView> {
        val problems = resolver.problems(state.steps)
        return (0 until slots).map { slot ->
            when {
                slot < state.steps.size -> step(state, slot, lines.of(state.steps, slot, state.progress.met), problems[slot], facts)
                slot == state.steps.size -> SlotView.Add
                else -> SlotView.Free
            }
        }
    }

    /** "Flow builder: Willows (changed)" for a saved flow, else "Flow builder" (Maxime, 2026-10-10). */
    fun title(state: IdleState): String = savedFlows.label(state)?.let { "Flow builder: $it" } ?: "Flow builder"

    fun status(state: IdleState, slots: Int): String {
        val now = if (state.running) {
            "Running step ${state.stepIndex + 1} of ${state.steps.size}, lap ${state.laps + 1}."
        } else {
            "Stopped."
        }
        return "$now  @gry@${state.steps.size} of $slots steps"
    }

    /** The Reflexes tab's rows: each reflex, then the row that adds one, then free rows up to [reflexSlots]. */
    fun reflexRows(state: IdleState, reflexSlots: Int): List<ReflexRowView> {
        val problems = reflexes.problems(state.steps, state.reflexes)
        return (0 until reflexSlots).map { row ->
            val reflex = state.reflexes.getOrNull(row)
            when {
                reflex != null -> reflexRow(state, row, reflex, problems[row])
                row == state.reflexes.size -> ReflexRowView.Add
                else -> ReflexRowView.Free
            }
        }
    }

    /** Every widget of the overview: slots, reflex rows, the Travel page, status, levels toggle and buttons. */
    fun updates(state: IdleState, slots: Int, reflexSlots: Int, facts: OptionFacts): List<WidgetUpdate> =
        slots(state, slots, facts).withIndex().flatMap { (slot, view) -> slotUpdates(slot, view) } +
            reflexRows(state, reflexSlots).withIndex().flatMap { (row, view) -> reflexRowUpdates(row, view) } + travel.updates(state, facts.worn) +
            controls(state, slots)

    /** The kind picker: its title and a button per kind of step, the buttons left over hidden. */
    fun kinds(state: IdleState): List<WidgetUpdate> =
        listOf(WidgetUpdate.Text(BuilderWidgets.KINDS_TITLE, "What should step ${state.steps.size + 1} do?")) +
            (0 until BuilderWidgets.KIND_BUTTONS).flatMap { button ->
                val type = types.all.getOrNull(button)
                listOf(WidgetUpdate.Visible(BuilderWidgets.kindButton(button), visible = type != null)) + listOfNotNull(
                    type?.let { WidgetUpdate.Picture(BuilderWidgets.kindPicture(button), WidgetPicture.of(it.icon(StepSettings(it.kind)))) },
                    type?.let { WidgetUpdate.Text(BuilderWidgets.kindLabel(button), capitalised(it.label)) },
                )
            }

    /** Shows [page]: the overview with the tab it names lit (the other tabs' areas hidden), or another screen. */
    fun page(page: BuilderPage): List<WidgetUpdate> {
        val reflexTab = page == BuilderPage.REFLEXES
        val travelTab = page == BuilderPage.TRAVEL
        val stepsTab = !reflexTab && !travelTab
        return listOf(
            WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = page == BuilderPage.OVERVIEW || reflexTab || travelTab),
            WidgetUpdate.Visible(BuilderWidgets.SLOTS, visible = stepsTab),
            WidgetUpdate.Visible(BuilderWidgets.REFLEX_ROWS, visible = reflexTab),
            WidgetUpdate.Text(BuilderWidgets.STEPS_TAB, lit("Steps", stepsTab)),
            WidgetUpdate.Colour(BuilderWidgets.STEPS_TAB_FRAME, frame(lit = stepsTab)),
            WidgetUpdate.Text(BuilderWidgets.REFLEXES_TAB, lit("Reflexes", reflexTab)),
            WidgetUpdate.Colour(BuilderWidgets.REFLEXES_TAB_FRAME, frame(lit = reflexTab)),
            WidgetUpdate.Visible(BuilderWidgets.TRAVEL, visible = travelTab),
            WidgetUpdate.Text(BuilderWidgets.TRAVEL_TAB, lit("Travel", travelTab)),
            WidgetUpdate.Colour(BuilderWidgets.TRAVEL_TAB_FRAME, frame(lit = travelTab)),
            WidgetUpdate.Visible(BuilderWidgets.ATTACH_ALL_REFLEXES_LAYER, visible = stepsTab),
            WidgetUpdate.Visible(BuilderWidgets.KINDS, visible = page == BuilderPage.KINDS),
            WidgetUpdate.Visible(BuilderWidgets.CONFIGURE, visible = page == BuilderPage.CONFIGURE),
        )
    }

    private fun reflexRow(state: IdleState, row: Int, reflex: ReflexSettings, problem: String?): ReflexRowView.Reflex {
        val sentence = font.fit(form.sentence(reflex, state.steps), BuilderWidgets.SENTENCE_ROOM)
        return ReflexRowView.Reflex(
            number = row + 1,
            picture = WidgetPicture.of(form.icon(reflex)),
            sentence = if (problem != null) "@red@$sentence" else sentence,
            frame = if (problem != null) BuilderWidgets.PROBLEM else BuilderWidgets.EDGE,
        )
    }

    private fun reflexRowUpdates(row: Int, view: ReflexRowView): List<WidgetUpdate> {
        val reflex = view as? ReflexRowView.Reflex
        val text = reflex?.sentence ?: if (view == ReflexRowView.Add) ADD_REFLEX else ""
        return listOf(
            WidgetUpdate.Text(BuilderWidgets.reflexRowNumber(row), reflex?.number?.toString().orEmpty()),
            WidgetUpdate.Picture(BuilderWidgets.reflexRowPicture(row), reflex?.picture ?: WidgetPicture.None),
            WidgetUpdate.Text(BuilderWidgets.reflexRowText(row), text),
            WidgetUpdate.Colour(BuilderWidgets.reflexRowFrame(row), reflex?.frame ?: if (view == ReflexRowView.Add) BuilderWidgets.EDGE else BuilderWidgets.FREE),
        )
    }

    /**
     * The slot's lines are the step's words, the target in grey while nothing is picked, the goal on up to two lines,
     * green once met (S12), then the held lines: the reason, the wait, what a trip does on its way in white with no "! "
     * (Q7) and the dangers, as many as the lines a reason may take; a danger shows whole or not at all.
     */
    private fun step(state: IdleState, slot: Int, words: SlotWords, problem: String?, facts: OptionFacts): SlotView.Step {
        val icon = words.icon?.let { WidgetPicture.of(it) } ?: WidgetPicture.None
        val running = state.running && state.stepIndex == slot
        val reason = state.blocked?.takeIf { running } ?: problem
        val waiting = state.waiting?.takeIf { running }
        val underWay = state.underWay?.takeIf { running }
        val targetLine = words.picked?.let { it.label?.let(::fitted) ?: NOT_SET }
        val goalLines = words.goal?.let { goal -> font.wrap(goal, BuilderWidgets.LINE_ROOM, GOAL_LINES).map { if (slot in state.progress.met) "@gre@$it" else it } }
        val details = listOfNotNull(targetLine) + words.details.map(::fitted) + goalLines.orEmpty()
        val held = (reason?.let { heldLines(it, "@red@") }.orEmpty() + waiting?.let { heldLines(it, "@yel@") }.orEmpty() +
            underWay?.let { font.wrap(it, BuilderWidgets.LINE_ROOM, REASON_LINES).map { line -> "@whi@$line" } }.orEmpty()).take(REASON_LINES)
        val threats = dangers.of(state.steps, slot, facts).map { heldLines(it.short, "@yel@") }
        val shown = threats.fold(held) { lines, threat -> if (lines.size + threat.size <= REASON_LINES) lines + threat else lines }
        val pictures = StepPictures.of(icon, words.picked?.icon)
        return SlotView.Step(
            number = slot + 1,
            picture = pictures.picture,
            corner = pictures.corner,
            kind = words.kind,
            lines = details.take(BuilderWidgets.SLOT_LINES - shown.size) + shown,
            frame = when {
                reason != null -> BuilderWidgets.PROBLEM
                running -> BuilderWidgets.RUNNING
                else -> BuilderWidgets.EDGE
            },
        )
    }

    private fun slotUpdates(slot: Int, view: SlotView): List<WidgetUpdate> {
        val step = view as? SlotView.Step
        val lines = step?.lines.orEmpty()
        val corner = step?.corner ?: WidgetPicture.None
        return listOf(
            WidgetUpdate.Text(BuilderWidgets.slotNumber(slot), step?.number?.toString().orEmpty()),
            WidgetUpdate.Picture(BuilderWidgets.slotPicture(slot), step?.picture ?: WidgetPicture.None),
            WidgetUpdate.Picture(BuilderWidgets.slotCorner(slot), corner),
            WidgetUpdate.Visible(BuilderWidgets.slotCornerLayer(slot), visible = corner != WidgetPicture.None),
            WidgetUpdate.Text(BuilderWidgets.slotKind(slot), step?.kind.orEmpty()),
        ) + (0 until BuilderWidgets.SLOT_LINES).map { WidgetUpdate.Text(BuilderWidgets.slotLine(slot, it), lines.getOrNull(it).orEmpty()) } +
            listOf(
                WidgetUpdate.Text(BuilderWidgets.slotPlus(slot), if (view == SlotView.Add) "+" else ""),
                WidgetUpdate.Text(BuilderWidgets.slotAdd(slot), if (view == SlotView.Add) "Add step" else ""),
                WidgetUpdate.Colour(BuilderWidgets.slotFrame(slot), step?.frame ?: if (view == SlotView.Add) BuilderWidgets.EDGE else BuilderWidgets.FREE),
            )
    }

    private fun controls(state: IdleState, slots: Int): List<WidgetUpdate> {
        val boosted = state.countBoostedLevels
        val editable = !state.running && (state.steps.isNotEmpty() || state.reflexes.isNotEmpty())
        return stops(state) + listOf(
            WidgetUpdate.Text(BuilderWidgets.TITLE, title(state)),
            WidgetUpdate.Text(BuilderWidgets.STATUS, status(state, slots)),
            WidgetUpdate.Text(BuilderWidgets.BASE_LEVELS, lit("Base", !boosted)),
            WidgetUpdate.Colour(BuilderWidgets.BASE_LEVELS_FRAME, frame(lit = !boosted)),
            WidgetUpdate.Text(BuilderWidgets.BOOSTED_LEVELS, lit("Boosted", boosted)),
            WidgetUpdate.Colour(BuilderWidgets.BOOSTED_LEVELS_FRAME, frame(lit = boosted)),
            WidgetUpdate.Text(BuilderWidgets.SAVE_FLOW, usable("Save", state.steps.isNotEmpty())),
            WidgetUpdate.Text(BuilderWidgets.RUN, usable("Run", state.steps.isNotEmpty())),
            WidgetUpdate.Text(BuilderWidgets.STOP, usable("Stop", state.running)),
            WidgetUpdate.Text(BuilderWidgets.CLEAR, usable("Clear", editable)),
            WidgetUpdate.Text(
                BuilderWidgets.ATTACH_ALL_REFLEXES,
                usable("Attach all reflexes", !state.running && state.steps.isNotEmpty() && state.reflexes.isNotEmpty()),
            ),
        )
    }

    /** The Flow stops row (S12): the chosen stop lit as the Levels toggle is, the number box shown for laps and time. */
    private fun stops(state: IdleState): List<WidgetUpdate> {
        val stop = state.stop
        return FlowStops.CHOICES.withIndex().flatMap { (choice, option) ->
            val chosen = option.value == stop.kind
            listOf(
                WidgetUpdate.Text(BuilderWidgets.stopChoice(choice), lit(option.word, chosen)),
                WidgetUpdate.Colour(BuilderWidgets.stopFrame(choice), frame(lit = chosen)),
            )
        } + listOf(
            WidgetUpdate.Visible(BuilderWidgets.STOPS_AFTER, visible = FlowStops.typed(stop.kind)),
            WidgetUpdate.Text(BuilderWidgets.STOPS_AFTER_TEXT, FlowStops.afterWords(stop) ?: NOT_TYPED),
        )
    }

    private fun fitted(line: String): String = font.fit(line, BuilderWidgets.LINE_ROOM)

    /** A block, a wait or a danger under the step's lines, in [colour], on the lines a reason may take. */
    private fun heldLines(text: String, colour: String): List<String> =
        font.wrap("! $text", BuilderWidgets.LINE_ROOM, REASON_LINES).map { line -> "$colour$line" }

    private fun lit(text: String, on: Boolean): String = if (on) "@whi@$text" else text

    private fun frame(lit: Boolean): Int = if (lit) BuilderWidgets.LIT else BuilderWidgets.UNLIT

    private fun usable(text: String, can: Boolean): String = if (can) text else "@gry@$text"


    private companion object {
        const val NOT_SET = "@gry@${SlotWords.NOT_SET}"
        const val NOT_TYPED = "@gry@not set"
        const val ADD_REFLEX = "@gry@+ Add reflex"

        /** A reason may take every line but the first, which keeps the target (Maxime, 2026-10-09). */
        const val REASON_LINES = BuilderWidgets.SLOT_LINES - 1

        /** "Goal met: Woodcutting 38" does not fit one line of the small font (Maxime, 2026-10-15 live). */
        const val GOAL_LINES = 2
    }
}
