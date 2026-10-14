package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.ItemOptions
import game.idle.flow.option.SkillOptions
import game.idle.flow.option.StepTarget
import io.luna.game.model.mob.Skill

/**
 * A step's Until goal (S12, Maxime's S01 decision): met, the step is skipped for the rest of the run or the whole flow
 * stops. A [Level] in any skill, the step's own by default; a [Count] of what the step's Amount counts, over every lap
 * since Run; a [Time] since Run; or the [Bank] holding an amount of an item.
 */
sealed interface Goal {

    data class Level(val skill: Int, val level: Int) : Goal

    data class Count(val total: Int) : Goal

    data class Time(val minutes: Int) : Goal

    /** The bank holding [amount] of [item], counting every item of its name ([items]). */
    data class Bank(val item: Int, val items: Set<Int>, val amount: Int) : Goal
}

/** What happens once a step's goal is met: the step is skipped for the rest of the run (the default), or the flow stops. */
enum class WhenMet(val value: String, val word: String) {
    SKIP("skip", "Skip this step"),
    STOP("stop", "Stop the flow"),
}

data class ResolvedGoal(val goal: Goal, val whenMet: WhenMet)

/** The goal's words, items named with [names]: the slot's line, what the chat lines name, the line once it is met. */
object GoalWords {

    /** "Strength 20", "100 in all", "30 min", "100 oak logs in the bank". */
    fun short(goal: Goal, names: GameNames): String =
        when (goal) {
            is Goal.Level -> "${Skill.getName(goal.skill)} ${goal.level}"
            is Goal.Count -> "${StepAmount.short(goal.total)} in all"
            is Goal.Time -> "${goal.minutes} min"
            is Goal.Bank -> "${StepAmount.short(goal.amount)} ${names.item(goal.item).lowercase()} in the bank"
        }

    /** The slot's line while the goal is not met: "until Strength 20", "for 30 min", "until the bank has 100 oak logs". */
    fun detail(goal: Goal, names: GameNames): String =
        when (goal) {
            is Goal.Level, is Goal.Count -> "until ${short(goal, names)}"
            is Goal.Time -> "for ${short(goal, names)}"
            is Goal.Bank -> "until the bank has ${StepAmount.short(goal.amount)} ${names.item(goal.item).lowercase()}"
        }

    /** The slot's line once the goal is met: "Goal met: Strength 20". */
    fun met(goal: Goal, names: GameNames): String = "$MET${short(goal, names)}"

    const val MET = "Goal met: "
}

/**
 * The Until rows of a step's configure screen and the goal they describe, kind-free: every counted kind has them
 * ([StepType.goal]). Kept under [KEY] (the goal's kind, none without), [SKILL] and [LEVEL], [COUNT] (the total, or
 * what the bank must hold), [MINUTES], [ITEM] (one of [items]) and [MET]; a missing input is the step's red reason.
 */
class GoalForm(private val items: ItemCatalog) {

    /** The rows, on the right above the work spot's: Until, the picked kind's inputs, When met (Maxime, 2026-10-15). */
    fun fields(type: StepType): List<StepField> =
        listOf(
            StepField.Toggle(KEY, "Until", KINDS, current = { settings, _ -> settings[KEY].orEmpty() }, column = FieldColumn.RIGHT),
            StepField.Search(
                "Skill", StepTarget(SKILL, SkillOptions, defaultOf = { settings -> type.skill(settings)?.let { Skill.getName(it).lowercase() } }),
                "Which skill?", visible = { it[KEY] == LEVEL_GOAL }, column = FieldColumn.RIGHT,
            ),
            StepField.Typed(LEVEL, "Level", LEVEL_RANGE, LEVEL_RULE, shown = { it?.let { level -> "level $level" } ?: NOT_SET }, column = FieldColumn.RIGHT, visible = { it[KEY] == LEVEL_GOAL }),
            StepField.Typed(COUNT, "Count", StepAmount.RANGE, COUNT_RULE, shown = { it?.let { count -> "${StepAmount.short(count)} in all" } ?: NOT_SET }, column = FieldColumn.RIGHT, visible = { it[KEY] == COUNT_GOAL }),
            StepField.Typed(MINUTES, "Minutes", StepAmount.RANGE, MINUTES_RULE, shown = { it?.let { minutes -> "$minutes min" } ?: NOT_SET }, column = FieldColumn.RIGHT, visible = { it[KEY] == TIME_GOAL }),
            StepField.Search("Item", StepTarget(ITEM, ItemOptions(items)), "Which item in the bank?", visible = { it[KEY] == BANK_GOAL }, column = FieldColumn.RIGHT),
            StepField.Typed(COUNT, "Count", StepAmount.RANGE, COUNT_RULE, shown = { it?.let { count -> "${StepAmount.short(count)} in the bank" } ?: NOT_SET }, column = FieldColumn.RIGHT, visible = { it[KEY] == BANK_GOAL }),
            StepField.Toggle(
                MET, "When met", WhenMet.entries.map { Choice(it.value, it.word) },
                current = { settings, _ -> whenMet(settings).value }, column = FieldColumn.RIGHT, visible = { it[KEY] != null },
            ),
        )

    /** The goal [settings] hold for a step of [type], null for none; throws [FlowError] naming what is wrong. */
    fun read(settings: StepSettings, type: StepType): ResolvedGoal? {
        val kind = settings[KEY] ?: return null
        return ResolvedGoal(goal(kind, settings, type), whenMet(settings))
    }

    private fun goal(kind: String, settings: StepSettings, type: StepType): Goal =
        when (kind) {
            LEVEL_GOAL -> Goal.Level(skill(settings, type), number(settings, LEVEL, LEVEL_LABEL, LEVEL_WORDS, LEVEL_RANGE))
            COUNT_GOAL -> Goal.Count(count(settings))
            TIME_GOAL -> Goal.Time(number(settings, MINUTES, MINUTES_LABEL, ONE_OR_MORE, StepAmount.RANGE))
            BANK_GOAL -> bank(settings)
            else -> throw FlowError("'$kind' is not a kind of goal")
        }

    private fun skill(settings: StepSettings, type: StepType): Int {
        val name = settings[SKILL] ?: return type.skill(settings) ?: throw FlowError(Reasons.needs(SKILL_LABEL))
        return Skill.NAMES.indexOfFirst { it.equals(name, ignoreCase = true) }.takeIf { it >= 0 } ?: throw FlowError(Reasons.notA(SKILL_LABEL, name))
    }

    private fun bank(settings: StepSettings): Goal.Bank {
        val text = settings[ITEM] ?: throw FlowError(Reasons.needs(ITEM_LABEL))
        val id = text.toIntOrNull()?.let { items.alike(it).ifEmpty { null } }?.let { text.toInt() } ?: throw FlowError(Reasons.notA(ITEM_LABEL, text))
        return Goal.Bank(id, items.alike(id), count(settings))
    }

    private fun count(settings: StepSettings): Int = number(settings, COUNT, COUNT_LABEL, ONE_OR_MORE, StepAmount.RANGE)

    /** A whole number of [range] under [key], the rule naming the row [label]; a range open at the top checks its start only. */
    private fun number(settings: StepSettings, key: String, label: String, words: String, range: IntRange): Int {
        val text = settings[key] ?: throw FlowError(Reasons.needs(label, countable = label != MINUTES_LABEL))
        val value = text.toIntOrNull()
        if (value == null || value < range.first || value > range.last) throw FlowError(Reasons.takes(label, words, text))
        return value
    }

    private fun whenMet(settings: StepSettings): WhenMet {
        val value = settings[MET] ?: return WhenMet.SKIP
        return WhenMet.entries.firstOrNull { it.value == value } ?: throw FlowError("'$value' is not a choice for When met")
    }

    companion object {
        const val KEY = "goal"
        const val LEVEL_GOAL = "level"
        const val COUNT_GOAL = "count"
        const val TIME_GOAL = "time"
        const val BANK_GOAL = "bank"
        const val SKILL = "goalSkill"
        const val LEVEL = "goalLevel"
        const val COUNT = "goalCount"
        const val MINUTES = "goalMinutes"
        const val ITEM = "goalItem"
        const val MET = "goalMet"

        private const val SKILL_LABEL = "Skill"
        private const val LEVEL_LABEL = "Level"
        private const val COUNT_LABEL = "Count"
        private const val MINUTES_LABEL = "Minutes"
        private const val ITEM_LABEL = "Item"
        private const val LEVEL_WORDS = "1 to 99"
        private const val ONE_OR_MORE = "1 or more"
        private const val NOT_SET = "@gry@not set yet"

        val LEVEL_RANGE = 1..99
        val LEVEL_RULE = Reasons.takes(LEVEL_LABEL, LEVEL_WORDS)
        val COUNT_RULE = Reasons.takes(COUNT_LABEL, ONE_OR_MORE)
        val MINUTES_RULE = Reasons.takes(MINUTES_LABEL, ONE_OR_MORE)

        /** The Until toggle's buttons: none (no value kept) and the four kinds. */
        val KINDS = listOf(Choice("", "None"), Choice(LEVEL_GOAL, "Level"), Choice(COUNT_GOAL, "Count"), Choice(TIME_GOAL, "Time"), Choice(BANK_GOAL, "Bank"))
    }
}
