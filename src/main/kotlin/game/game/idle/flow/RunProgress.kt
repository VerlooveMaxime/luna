package game.idle.flow

/**
 * What a run has done so far (S12), saved in the player's state so a relog resumes the run as it was (Maxime,
 * 2026-10-15): the [totals] each step counted over the laps since Run, by step index; the steps whose goal is [met],
 * skipped for the rest of the run; when the run started ([startedAt], wall-clock milliseconds, which a Time goal and
 * the After-time stop count from); and, by bank step index, the withdrawals the player was [toldLeftOut] (S13).
 */
data class RunProgress(
    val totals: Map<Int, Int> = emptyMap(),
    val met: Set<Int> = emptySet(),
    val startedAt: Long = 0,
    val toldLeftOut: Map<Int, Set<Int>> = emptyMap(),
) {

    fun total(index: Int): Int = totals[index] ?: 0

    /** With [amount] more counted by the step at [index]. */
    fun counted(index: Int, amount: Int): RunProgress = if (amount == 0) this else copy(totals = totals + (index to total(index) + amount))

    fun meeting(index: Int): RunProgress = copy(met = met + index)

    /** Whether the player was told the bank step at [index] leaves out [item] this run. */
    fun told(index: Int, item: Int): Boolean = item in toldLeftOut[index].orEmpty()

    /** With the player told the bank step at [index] leaves out [items]. */
    fun telling(index: Int, items: Set<Int>): RunProgress = copy(toldLeftOut = toldLeftOut + (index to toldLeftOut[index].orEmpty() + items))
}

/** The time of day a run reads (a boundary): wall-clock milliseconds. */
fun interface Clock {

    fun now(): Long
}

/**
 * Which of a bank step's withdrawals feed only steps skipped for the run (S12, Maxime's S01 decision): a withdrawal is
 * left out when its item is an input of a met step and neither an input nor a tool of a step still running (feathers
 * are a make step's input and a fly fisher's bait), so the flow makes no useless trip; the player is told so in words
 * (S13: a chat line, the bank slot's line).
 */
object SkippedFeeds {

    /** The item ids [bank] withdraws for met steps alone, among [steps] (a step that does not resolve feeds nothing). */
    fun leftOut(steps: List<ResolvedStep?>, met: Set<Int>, bank: BankMoves): Set<Int> = fed(steps, met, bank).keys

    /** Each withdrawal [bank] leaves out, to the index of the first met step it fed. */
    fun fed(steps: List<ResolvedStep?>, met: Set<Int>, bank: BankMoves): Map<Int, Int> {
        val running = needs(steps.filterIndexed { index, _ -> index !in met }).flatMap { it.inputs + it.tools.flatMap { tool -> tool.items.keys } }.toSet()
        val fedBy = steps.withIndex().filter { it.index in met }
            .flatMap { (index, step) -> needs(listOf(step)).flatMap { it.inputs }.map { item -> item to index } }
            .groupBy({ it.first }, { it.second })
        return bank.withdraws.filter { it !in running && it in fedBy }.associateWith { fedBy.getValue(it).first() }
    }

    private fun needs(steps: List<ResolvedStep?>): List<StepNeeds> = steps.flatMap { it?.needs().orEmpty() }
}
