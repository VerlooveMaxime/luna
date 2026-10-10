package game.idle.flow

import game.idle.flow.ReflexKeys.BELOW
import game.idle.flow.ReflexKeys.BELOW_RANGE
import game.idle.flow.ReflexKeys.BELOW_RULE
import game.idle.flow.ReflexKeys.DEFAULT_BELOW
import game.idle.flow.ReflexKeys.DO
import game.idle.flow.ReflexKeys.EAT
import game.idle.flow.ReflexKeys.FOODS
import game.idle.flow.ReflexKeys.JUMP
import game.idle.flow.ReflexKeys.RUN_AWAY
import game.idle.flow.ReflexKeys.STEP
import game.idle.flow.ReflexKeys.STOP
import game.idle.flow.ReflexKeys.THEN
import game.idle.flow.option.GameNames
import game.idle.flow.option.JumpTargetOptions
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepTarget

/**
 * A reflex as the builder shows it (S07c): its screen's fields, header and warning, and the sentence and picture its
 * row shows. Its screen edits it as settings like a step's ([settings]), so the configure screen's toggles, typed
 * fields, lists and searches work on it unchanged. An eat reflex picks from [foods]; [portions] gives every portion of
 * the food each edible id belongs to; a jump picks one of the flow's steps, worded with [types]; items are named with
 * [names].
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

    /** The screen's rows: Do, When, then the Food list on Eat, or Then and, on Jump, the step, picked among [steps]. */
    fun fields(steps: List<StepSettings>): List<StepField> =
        listOf(
            StepField.Toggle(DO, "Do", listOf(Choice(EAT, "Eat"), Choice(RUN_AWAY, "Run away")), current = { settings, _ -> doing(settings[DO]) }),
            StepField.Typed(BELOW, "When", BELOW_RANGE, BELOW_RULE, shown = { "Hitpoints below ${it ?: DEFAULT_BELOW}%" }),
            StepField.Items(
                FOODS, "Food", foods, "What would you like to eat?", "+ Add or remove food...", rows = FOOD_ROWS,
                visible = { doing(it[DO]) == EAT }, empty = "+ Any food: pick some...",
            ),
            StepField.Toggle(
                THEN, "Then", listOf(Choice(STOP, "Stop the flow"), Choice(JUMP, "Jump to a step")),
                current = { settings, _ -> settings[THEN] ?: STOP }, visible = { doing(it[DO]) == RUN_AWAY },
            ),
            StepField.Search(
                "Step", StepTarget(STEP, JumpTargetOptions(steps, types, names)), "Which step should it jump to?",
                visible = { doing(it[DO]) == RUN_AWAY && it[THEN] == JUMP }, missing = "Deleted step",
            ),
        )

    /** What the reflex does, as its screen's header names it: "Eat", "Run away". */
    fun label(reflex: ReflexSettings): String = if (doing(reflex[DO]) == EAT) "Eat" else "Run away"

    fun description(reflex: ReflexSettings): String =
        if (doing(reflex[DO]) == EAT) "Eats when hitpoints fall below a share of full." else "Runs from what attacks you, then goes on or stops the flow."

    /** The first food it eats, shrimps when it eats any; leather boots for running away. */
    fun icon(reflex: ReflexSettings): StepIcon =
        if (doing(reflex[DO]) == EAT) StepIcon.Item(foodIds(reflex).firstOrNull() ?: SHRIMPS) else StepIcon.Item(LEATHER_BOOTS)

    /**
     * The reflex in a sentence, its jump numbered as the step stands in [steps]: "Hitpoints below 50%: eat trout,
     * lobster", "Hitpoints below 50%: run away, then jump to step 2".
     */
    fun sentence(reflex: ReflexSettings, steps: List<StepSettings>): String {
        val what = if (doing(reflex[DO]) == EAT) "eat ${eats(reflex)}" else "run away, then ${afterwards(reflex, steps)}"
        return "Hitpoints below ${reflex[BELOW] ?: DEFAULT_BELOW}%: $what"
    }

    /** What an eat reflex needs carried or withdrawn: one of its foods, any food when it lists none; null for a run. */
    fun need(reflex: ReflexSettings): ToolNeed? {
        if (doing(reflex[DO]) != EAT) return null
        val ids = foodIds(reflex).flatMap { portions[it].orEmpty() }.ifEmpty { portions.keys }
        return ToolNeed("food", ids.associateWith { 0 }, countable = false)
    }

    private fun eats(reflex: ReflexSettings): String =
        foodIds(reflex).joinToString(", ") { names.item(it).lowercase() }.ifEmpty { "any food" }

    private fun afterwards(reflex: ReflexSettings, steps: List<StepSettings>): String {
        if (reflex[THEN] != JUMP) return "stop the flow"
        val id = reflex[STEP]?.toIntOrNull() ?: return "jump to a step"
        val index = steps.indexOfFirst { it.id == id }
        return if (index < 0) "jump to a deleted step" else "jump to step ${index + 1}"
    }

    private fun foodIds(reflex: ReflexSettings): List<Int> = StepItems.parse(reflex[FOODS]).map { it.id }

    /** A reflex without a Do eats; one with a Do no reflex has shows as running away, its screen saying what is wrong. */
    private fun doing(value: String?): String = value ?: EAT

    companion object {
        /** The kind a reflex's settings carry on its screen, which no step has. */
        const val KIND = "reflex"

        const val FOOD_ROWS = 4
        const val SHRIMPS = 315
        const val LEATHER_BOOTS = 1061
    }
}
