package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FlowContext
import game.idle.flow.FlowResolver
import game.idle.flow.SavedFlows
import game.idle.flow.StepSettings
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepOption

/** The builder's screens the server shows one at a time: the overview, the kind picker and a step's configure screen. */
enum class BuilderPage { OVERVIEW, KINDS, CONFIGURE }

/** The pictures a step shows: its target's [picture] with the kind's icon in the [corner], or the kind's icon alone. */
data class StepPictures(val picture: WidgetPicture, val corner: WidgetPicture) {

    companion object {
        /** [icon] is the kind's, [picked] the option the step's target picked, null for none. */
        fun of(icon: WidgetPicture, picked: StepOption?): StepPictures =
            picked?.let { StepPictures(WidgetPicture.of(it.icon), icon) } ?: StepPictures(icon, WidgetPicture.None)
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

/**
 * What the builder's overview and kind picker show for a player's [IdleState] (flow builder v2, S06a, the mockup's
 * screens): the title naming the saved flow it holds, of [savedFlows]; a slot per step slot, each step with its target,
 * kind and details, framed green while it runs and red with its reason when it cannot work (Maxime, 2026-10-09: what
 * the resolver refuses, or the running step's block); then the status line, the levels toggle and the buttons. Texts
 * are fitted to the client's small font, [font]; a reason takes the last lines, up to three, the details giving way to
 * it.
 */
class BuilderOverview(
    private val resolver: FlowResolver,
    private val names: GameNames,
    private val font: ClientFont,
    private val savedFlows: SavedFlows,
) {

    private val types = resolver.types

    fun slots(state: IdleState, slots: Int): List<SlotView> {
        val problems = resolver.problems(state.steps)
        return (0 until slots).map { slot ->
            val settings = state.steps.getOrNull(slot)
            when {
                settings != null -> step(state, slot, settings, problems[slot], resolver.contextBefore(state.steps, slot))
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

    /** Every widget of the overview: slots, status, levels toggle and buttons. */
    fun updates(state: IdleState, slots: Int): List<WidgetUpdate> =
        slots(state, slots).withIndex().flatMap { (slot, view) -> slotUpdates(slot, view) } + controls(state, slots)

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

    fun page(page: BuilderPage): List<WidgetUpdate> =
        listOf(
            WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = page == BuilderPage.OVERVIEW),
            WidgetUpdate.Visible(BuilderWidgets.KINDS, visible = page == BuilderPage.KINDS),
            WidgetUpdate.Visible(BuilderWidgets.CONFIGURE, visible = page == BuilderPage.CONFIGURE),
        )

    private fun step(state: IdleState, slot: Int, settings: StepSettings, problem: String?, before: FlowContext): SlotView.Step {
        val type = types.find(settings.kind)
        val icon = type?.let { WidgetPicture.of(it.icon(settings)) } ?: WidgetPicture.None
        val target = type?.target(names)
        val picked = target?.picked(settings)
        val running = state.running && state.stepIndex == slot
        val reason = state.blocked?.takeIf { running }?.let(AutopilotStatus::reason) ?: problem
        val targetLine = target?.let { (picked?.label ?: settings[it.key])?.let(::fitted) ?: NOT_SET }
        val details = (listOfNotNull(targetLine) + type?.details(settings, before).orEmpty().map(::fitted))
        val reasonLines = reason?.let { font.wrap("! $it", BuilderWidgets.LINE_ROOM, REASON_LINES).map { line -> "@red@$line" } }.orEmpty()
        val pictures = StepPictures.of(icon, picked)
        return SlotView.Step(
            number = slot + 1,
            picture = pictures.picture,
            corner = pictures.corner,
            kind = capitalised(type?.label ?: settings.kind),
            lines = details.take(BuilderWidgets.SLOT_LINES - reasonLines.size) + reasonLines,
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
        val editable = !state.running && state.steps.isNotEmpty()
        return listOf(
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
        )
    }

    private fun fitted(line: String): String = font.fit(line, BuilderWidgets.LINE_ROOM)

    private fun lit(text: String, on: Boolean): String = if (on) "@whi@$text" else text

    private fun frame(lit: Boolean): Int = if (lit) BuilderWidgets.LIT else BuilderWidgets.UNLIT

    private fun usable(text: String, can: Boolean): String = if (can) text else "@gry@$text"


    private companion object {
        const val NOT_SET = "@gry@not set yet"

        /** A reason may take every line but the first, which keeps the target (Maxime, 2026-10-09). */
        const val REASON_LINES = BuilderWidgets.SLOT_LINES - 1
    }
}

/** [word] with a capital first letter, as the builder shows a kind's name. */
internal fun capitalised(word: String): String = word.take(1).uppercase() + word.drop(1)
