package game.idle.flow

/**
 * What a run has done so far (S12), saved in the player's state so a relog resumes the run as it was (Maxime,
 * 2026-10-15): the [totals] each step counted over the laps since Run, by step index; the steps whose goal is [met],
 * skipped for the rest of the run; and when the run started ([startedAt], wall-clock milliseconds, which a Time goal
 * and the After-time stop count from).
 */
data class RunProgress(
    val totals: Map<Int, Int> = emptyMap(),
    val met: Set<Int> = emptySet(),
    val startedAt: Long = 0,
) {

    fun total(index: Int): Int = totals[index] ?: 0

    /** With [amount] more counted by the step at [index]. */
    fun counted(index: Int, amount: Int): RunProgress = if (amount == 0) this else copy(totals = totals + (index to total(index) + amount))

    fun meeting(index: Int): RunProgress = copy(met = met + index)
}

/** The time of day a run reads (a boundary): wall-clock milliseconds. */
fun interface Clock {

    fun now(): Long
}

/**
 * Which of a bank step's withdrawals feed only steps skipped for the run (S12, Maxime's S01 decision): a withdrawal is
 * left out when its item is an input of a met step and neither an input nor a tool of a step still running (feathers
 * are a make step's input and a fly fisher's bait), so the flow makes no useless trip; the builder shows it struck
 * through.
 */
object SkippedFeeds {

    /** The item ids [bank] withdraws for met steps alone, among [steps] (a step that does not resolve feeds nothing). */
    fun leftOut(steps: List<ResolvedStep?>, met: Set<Int>, bank: BankMoves): Set<Int> {
        val skipped = needs(steps.filterIndexed { index, _ -> index in met }).flatMap { it.inputs }.toSet()
        val running = needs(steps.filterIndexed { index, _ -> index !in met }).flatMap { it.inputs + it.tools.flatMap { tool -> tool.items.keys } }.toSet()
        return bank.withdraws.filter { it in skipped && it !in running }.toSet()
    }

    private fun needs(steps: List<ResolvedStep?>): List<StepNeeds> = steps.flatMap { it?.needs().orEmpty() }
}
