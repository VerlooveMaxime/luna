package game.idle.flow

import game.idle.flow.ReflexKeys.ATTACKED
import game.idle.flow.ReflexKeys.BELOW
import game.idle.flow.ReflexKeys.BELOW_LABEL
import game.idle.flow.ReflexKeys.BELOW_RANGE
import game.idle.flow.ReflexKeys.BELOW_RULE
import game.idle.flow.ReflexKeys.DEFAULT_BELOW
import game.idle.flow.ReflexKeys.DO
import game.idle.flow.ReflexKeys.EAT
import game.idle.flow.ReflexKeys.FOODS
import game.idle.flow.ReflexKeys.HITPOINTS
import game.idle.flow.ReflexKeys.JUMP
import game.idle.flow.ReflexKeys.STEP
import game.idle.flow.ReflexKeys.STOP
import game.idle.flow.ReflexKeys.WHEN
import game.idle.flow.option.GameNames
import game.idle.flow.option.JumpTargetOptions
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget

/**
 * A reflex as the builder shows it (S07c): its screen's fields, header and warnings, and the sentence and picture its
 * row shows. Its screen edits it as settings like a step's ([settings]), so the configure screen's toggles, typed
 * fields, lists and searches work on it unchanged: When on the left with its value under it, Do on the right with what
 * it needs under it (S13, Maxime 2026-10-15). An eat reflex picks from [foods]; [portions] gives every portion of the
 * food each edible id belongs to; a jump picks one of the flow's steps, worded with [types]; items are named with [names].
 */
class ReflexForm(
    private val foods: OptionSource,
    private val portions: Map<Int, Set<Int>>,
    private val types: StepTypes,
    private val names: GameNames,
) {

    /** [reflex] as the settings its screen edits. */
    fun settings(reflex: ReflexSettings): StepSettings = StepSettings(KIND, reflex.values, reflex.id)

    /** The reflex [settings] hold. */
    fun reflex(settings: StepSettings): ReflexSettings = ReflexSettings(settings.id, settings.values)

    /** The screen's rows: When and Below on the left; Do, then the Food list on Eat or the step on Jump, picked among [steps]. */
    fun fields(steps: List<StepSettings>): List<StepField> =
        listOf(
            StepField.Toggle(
                WHEN, "When", listOf(Choice(HITPOINTS, "Hitpoints"), Choice(ATTACKED, "Under attack")),
                current = { settings, _ -> settings[WHEN] ?: HITPOINTS },
            ),
            StepField.Typed(BELOW, BELOW_LABEL, BELOW_RANGE, BELOW_RULE, shown = { "${it ?: DEFAULT_BELOW}%" }, visible = { !attacked(it[WHEN]) }),
            StepField.Toggle(
                DO, "Do", listOf(Choice(EAT, "Eat"), Choice(STOP, "Stop"), Choice(JUMP, "Jump")),
                current = { settings, _ -> doing(settings[DO]) }, column = FieldColumn.RIGHT,
            ),
            StepField.Items(
                FOODS, "Food", foods, "What would you like to eat?", "+ Add or remove food...", rows = FOOD_ROWS,
                column = FieldColumn.RIGHT, visible = { doing(it[DO]) == EAT }, empty = "+ Any food: pick some...",
            ),
            StepField.Search(
                "Step", StepTarget(STEP, JumpTargetOptions(steps, types, names)), "Which step should it jump to?",
                visible = { doing(it[DO]) == JUMP }, missing = "Deleted step", column = FieldColumn.RIGHT,
            ),
        )

    /** What the reflex does, as its screen's header names it: "Eat", "Stop the flow", "Jump to a step". */
    fun label(reflex: ReflexSettings): String =
        when (doing(reflex[DO])) {
            EAT -> "Eat"
            JUMP -> "Jump to a step"
            else -> "Stop the flow"
        }

    fun description(reflex: ReflexSettings): String =
        when (doing(reflex[DO])) {
            EAT -> "Eats the first of its foods the bag holds."
            JUMP -> "Goes on from another step, running clear first while attacked."
            else -> "Stops the flow, running clear first while attacked."
        }

    /** The first food it eats, shrimps when it eats any; the red click cross to stop; the hint arrow to jump. */
    fun icon(reflex: ReflexSettings): StepIcon =
        when (doing(reflex[DO])) {
            EAT -> StepIcon.Item(foodIds(reflex).firstOrNull() ?: SHRIMPS)
            JUMP -> JUMP_ICON
            else -> STOP_ICON
        }

    /**
     * The reflex in a sentence, its jump numbered as the step stands in [steps]: "Hitpoints below 50%: eat trout,
     * lobster", "Under attack: jump to step 2".
     */
    fun sentence(reflex: ReflexSettings, steps: List<StepSettings>): String {
        val what = when (doing(reflex[DO])) {
            EAT -> "eat ${eats(reflex)}"
            JUMP -> jumps(reflex, steps)
            else -> "stop the flow"
        }
        return "${whenWords(reflex)}: $what"
    }

    /**
     * The flow's [reflexes] as a step's attach search offers them (S07c2b), by id in the flow's order: "1 Hitpoints below
     * 50%: eat trout" with its picture, a jump numbered as the step stands in [steps].
     */
    fun attachOptions(reflexes: List<ReflexSettings>, steps: List<StepSettings>): List<StepOption> =
        reflexes.withIndex().map { (index, reflex) ->
            StepOption(reflex.id.toString(), "${index + 1} ${sentence(reflex, steps)}", OptionIcon.of(icon(reflex)), group = index)
        }

    /** What an eat reflex needs carried or withdrawn: one of its foods, any food when it lists none; null for the others. */
    fun need(reflex: ReflexSettings): ToolNeed? {
        if (doing(reflex[DO]) != EAT) return null
        val ids = foodIds(reflex).flatMap { portions[it].orEmpty() }.ifEmpty { portions.keys }
        return ToolNeed("food", ids.associateWith { 0 }, countable = false)
    }

    /** What its pair of When and Do wastes, in yellow on its screen (Maxime, 2026-10-15): it acts all the same. */
    fun warnings(reflex: ReflexSettings): List<String> =
        listOfNotNull(EATS_AT_FULL.takeIf { attacked(reflex[WHEN]) && doing(reflex[DO]) == EAT })

    private fun whenWords(reflex: ReflexSettings): String =
        if (attacked(reflex[WHEN])) "Under attack" else "Hitpoints below ${reflex[BELOW] ?: DEFAULT_BELOW}%"

    private fun eats(reflex: ReflexSettings): String =
        foodIds(reflex).joinToString(", ") { names.item(it).lowercase() }.ifEmpty { "any food" }

    private fun jumps(reflex: ReflexSettings, steps: List<StepSettings>): String {
        val id = reflex[STEP]?.toIntOrNull() ?: return "jump to a step"
        val index = steps.indexOfFirst { it.id == id }
        return if (index < 0) "jump to a deleted step" else "jump to step ${index + 1}"
    }

    private fun foodIds(reflex: ReflexSettings): List<Int> = StepItems.parse(reflex[FOODS]).map { it.id }

    private fun attacked(value: String?): Boolean = value == ATTACKED

    /** A reflex without a Do eats; one with a Do no reflex has shows as stopping, its screen saying what is wrong. */
    private fun doing(value: String?): String = value ?: EAT

    companion object {
        /** The kind a reflex's settings carry on its screen, which no step has. */
        const val KIND = "reflex"

        const val FOOD_ROWS = 4
        const val SHRIMPS = 315

        /** The red cross a click leaves on the ground (Maxime, 2026-10-15). */
        val STOP_ICON = StepIcon.Media("cross", 5)

        /** The yellow arrow that points at what a hint is about (Maxime, 2026-10-15). */
        val JUMP_ICON = StepIcon.Media("headicons_hint", 0)

        const val EATS_AT_FULL = "Eats while attacked, even at full hitpoints"
    }
}
