package game.idle.flow

import game.idle.flow.option.GameNames

/**
 * What a step's slot in the builder says (S06a), which overlay line 2 and the harness say too (Maxime, 2026-10-11,
 * Q6): the [kind]'s name and [icon], what the step [picked] (null for a kind that picks nothing), its [details] after
 * what the steps before it set up, its Until [goal] as a line ("until Strength 20", "Goal met: Strength 20" once met,
 * S12; null without one or while it is wrong, the reason showing instead), and its own [spot] as the setting keeps it,
 * null for From the flow.
 */
data class SlotWords(
    val kind: String,
    val icon: StepIcon?,
    val picked: StepPick?,
    val details: List<String>,
    val goal: String?,
    val spot: String?,
) {

    /** The lines under the kind: what was picked, [NOT_SET] while nothing is, then the details, then the goal. */
    val lines: List<String> get() = listOfNotNull(picked?.let { it.label ?: NOT_SET }) + details + listOfNotNull(goal)

    /** The slot on one line, the spot last: "Chop: Oak tree, 5 per lap at 3101, 3509"; a kind with no lines is its name. */
    val line: String
        get() {
            val under = lines.joinToString(", ")
            val at = spot?.let { " at ${it.replace(" ", ", ")}" }.orEmpty()
            return if (under.isEmpty()) "$kind$at" else "$kind: $under$at"
        }

    companion object {
        const val NOT_SET = "not set yet"
    }
}

/** The words of every slot of a flow over [resolver]'s kinds, items named with [names]. */
class StepLines(private val resolver: FlowResolver, private val names: GameNames) {

    /**
     * The step at [index] of [steps] while the steps at [met] met their goals this run: its own goal met or not, a bank
     * step's withdrawals for met steps alone left out (S13); a step of a kind no type knows shows its kind alone.
     */
    fun of(steps: List<StepSettings>, index: Int, met: Set<Int> = emptySet()): SlotWords {
        val settings = steps[index]
        val type = resolver.types.find(settings.kind)
        return SlotWords(
            kind = capitalised(type?.label ?: settings.kind),
            icon = type?.icon(settings),
            picked = type?.pick(settings, names),
            details = type?.let { details(it, steps, index, met) }.orEmpty(),
            goal = goal(settings)?.let { if (index in met) GoalWords.met(it.goal, names) else GoalWords.detail(it.goal, names) },
            spot = settings[StepSpot.KEY],
        )
    }

    private fun details(type: StepType, steps: List<StepSettings>, index: Int, met: Set<Int>): List<String> {
        val context = resolver.contextBefore(steps, index)
        if (type !is LeavesOut || met.isEmpty()) return type.details(steps[index], context)
        val resolved = resolver.resolvedEach(steps)
        val leftOut = (resolved[index] as? BankMoves)?.let { SkippedFeeds.leftOut(resolved, met, it) }.orEmpty()
        return type.details(steps[index], context, leftOut)
    }

    private fun goal(settings: StepSettings): ResolvedGoal? =
        try {
            resolver.types.goal(settings)
        } catch (e: FlowError) {
            null
        }
}

/** [word] with a capital first letter, as the builder shows a kind's name. */
fun capitalised(word: String): String = word.take(1).uppercase() + word.drop(1)
