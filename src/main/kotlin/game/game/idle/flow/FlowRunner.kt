package game.idle.flow

import game.idle.autopilot.AutopilotActivity

/** The player a flow runs for: where each step's activity comes from, what its goals read and where its progress is saved. */
interface FlowPlayer {

    /** What the flow's reflexes see and do for the player, and whether they died. */
    val body: ReflexBody

    /** The activity of [step], whose trips say what they do through [speech]. */
    fun activity(step: ResolvedStep, speech: Speech): StepActivity

    fun saveStep(index: Int)

    /** The run's progress changed: a step's total, a goal met. */
    fun saveProgress(progress: RunProgress)

    /** The flow finished its last step and starts over. */
    fun lapCompleted()

    fun tell(message: String)

    /** The player's base level in [skill], which a Level goal counts (Maxime, 2026-10-15). */
    fun level(skill: Int): Int

    /** How many of [items] the bank holds, in all. */
    fun banked(items: Set<Int>): Int
}

/**
 * A flow resolved: its [steps] and, for each, the reflexes attached to it in its order (S07c) and its Until goal, null
 * for none (S12).
 */
data class ResolvedFlow(
    val steps: List<ResolvedStep>,
    val reflexes: List<List<ResolvedReflex>> = steps.map { emptyList() },
    val goals: List<ResolvedGoal?> = steps.map { null },
) {

    init {
        require(reflexes.size == steps.size) { "Each of the ${steps.size} steps needs its reflexes, not ${reflexes.size} lists" }
        require(goals.size == steps.size) { "Each of the ${steps.size} steps needs its goal or none, not ${goals.size}" }
    }
}

/**
 * Runs a flow step by step: each tick the current step's activity, guarded by its reflexes, acts until it says it is
 * done, then the next one starts, and after the last step the first one again, which counts as a lap. A reflex's jump
 * goes on from the step it names and never counts a lap (Maxime, 2026-10-10); a step cut short by a jump and cut short
 * again on its next turn before it made progress stops the flow, as does the player's death. A stop while something
 * attacks the player waits: they run until nothing does, then the flow stops (Maxime, 2026-10-10). A whole lap in
 * which no step worked stops the flow too, so a flow with nothing to do never spins (Maxime, 2026-10-11). The step index
 * is saved whenever it changes so a relog resumes where it was. What the steps report is put into [words] here, the
 * one place that knows their numbers.
 *
 * Goals (S12, Maxime 2026-10-15): a step whose goal is met is skipped for the rest of the run, or stops the flow; the
 * goal is read before the step starts and after each of its acts, ending the turn at once as the Amount does (Q14); a
 * Count goal adds up what every turn counted ([RunProgress.totals]); a bank step leaves out the withdrawals that feed
 * only skipped steps, telling the player once a run for each (S13). While a stop waits for the player to run clear,
 * that is what the flow waits for, as the slot and the overlay show it, and the step's block is no longer shown. The
 * flow [stop]s after a number of laps, after a number of minutes on the [clock] since the run started, or once every
 * goal is met, which is judged before the lap guard. The prayers the reflexes light are held for the whole run
 * ([PrayerHold]) and put out when the flow ends, however it ends (S13 part 3); a special attack lit is told once, the
 * bar then Luna's (S13 part 4).
 */
class FlowRunner(
    private val flow: ResolvedFlow,
    startIndex: Int,
    private val player: FlowPlayer,
    private val words: StepWords,
    private val clock: Clock,
    private val stop: FlowStop,
    progress: RunProgress,
    laps: Int,
) : AutopilotActivity {

    private val steps = flow.steps
    private var index = if (steps.isEmpty()) 0 else startIndex.coerceIn(0, steps.lastIndex)
    private var current: ReflexGuard? = null
    private var stopReason: String? = null
    private var progress = progress
    private var laps = laps

    /** The steps a jump cut short on their last turn. */
    private val cutShort = mutableSetOf<Int>()

    /** Whether any step worked in the lap under way. */
    private var lapWorked = false

    /** Why the flow stops, once nothing attacks the player. */
    private var stopped: String? = null

    /** Whether the current turn was ended for its goal: the step then settles its unit as the Amount's ending does. */
    private var ending = false

    /** Whether the player runs clear of what attacks them before the flow stops. */
    private var fleeing = false

    private val hold = PrayerHold()

    /**
     * Busy while the current step is, unless the flow has a reason to stop, the time is up or the step's goal is
     * reached: the kind's action would hold the player, so the next decision is taken at once to end the turn. Running
     * clear before the flow stops, only while the flight clicks a door.
     */
    override fun isBusy(): Boolean =
        if (stopping() != null) fleeing && player.body.flightBusy() else !timeUp() && (ending || reached() == null) && current?.isBusy() ?: false

    override fun stopReason(): String? = if (player.body.dead()) StepWords.DIED else stopped

    /** The current step's block; none between two steps, nor while the player runs clear before the flow stops. */
    override fun blocked(): String? = if (fleeing) null else current?.blocked()?.let(words::block)

    /** What the current step waits for, in the block's words, or running clear before the flow stops. */
    override fun waiting(): String? = (if (fleeing) Reason.RunningClear() else current?.waiting())?.let(words::block)

    /** What the current step's trip does on its way, or the flight's doors while the player runs clear before the flow stops. */
    override fun underWay(): String? = (if (fleeing) player.body.flightUnderWay() else current?.underWay())?.let(words::underWay)

    override fun act() {
        if (steps.isEmpty()) return
        if (stopping() == null) step()
        stopping()?.let(::leave)
        tellShut()
    }

    /** A door a flight shut behind the player is told once, before the stop or the jump it led to. */
    private fun tellShut() {
        player.body.shutBehind()?.let { player.tell(words.shutBehind(it)) }
    }

    /** The run is over, whatever ended it: the prayers it lit go out, each said. */
    override fun end() {
        hold.releaseAll(player.body).forEach { player.tell(words.prayed(it)) }
    }

    private fun step() {
        if (timeUp()) {
            stopReason = words.stoppedAfterMinutes((stop as FlowStop.AfterMinutes).minutes)
            return
        }
        val activity = current ?: begin() ?: return
        if (activity.isDone()) {
            advance(activity)
            return
        }
        if (!ending && reached() != null) {
            ending = true
            activity.endTurn()
        }
        activity.act()
        tellShut()
        activity.prayed().forEach { player.tell(words.prayed(it)) }
        activity.used().forEach { player.tell(words.usedSpecial(it)) }
        activity.said()?.let(::tell)
        activity.jump()?.let { jump(activity, it) }
    }

    /** Why the flow or its step wants to stop, held while the player runs from what attacks them. */
    private fun stopping(): String? = stopReason ?: current?.stopping()?.let { words.stop(index + 1, it) }

    private fun leave(reason: String) {
        fleeing = player.body.underAttack()
        if (fleeing) {
            player.body.flee()
            return
        }
        player.body.calm()
        stopped = reason
    }

    /**
     * The next step to start, skipping the ones met for the run and meeting the ones whose goal is reached before they
     * start; null when the flow stops instead, or when a whole pass found nothing to start (the lap guard then stops it).
     */
    private fun begin(): ReflexGuard? {
        var passed = 0
        while (stopReason == null && passed < steps.size) {
            meetBeforeStart()
            if (index !in progress.met) return startStep()
            pass()
            passed++
        }
        return null
    }

    /** A goal reached before its step starts is met and written at once. */
    private fun meetBeforeStart() {
        val goal = reached() ?: return
        meet(goal)
        player.saveProgress(progress)
    }

    private fun startStep(): ReflexGuard {
        val step = steps[index]
        val run = if (step is BankMoves) step.without(leaveOut(step)) else step
        val started = ReflexGuard(player.activity(run, Speech(::tell)), flow.reflexes[index], player.body, hold) { player.tell(words.tookOff(it)) }
        current = started
        ending = false
        return started
    }

    /** What the step or its trips say; a long way round once a run for each Travel choice, a relog included (Maxime, 2026-10-18). */
    private fun tell(said: Said) {
        if (said is Said.LongWay) {
            if (progress.toldLongWay(said.choice)) return
            progress = progress.tellingLongWay(said.choice)
            player.saveProgress(progress)
        }
        player.tell(words.said(index + 1, said))
    }

    /** What the bank step at [index] leaves out, each told the first time this run, a relog included. */
    private fun leaveOut(bank: BankMoves): Set<Int> {
        val fed = SkippedFeeds.fed(steps, progress.met, bank)
        val fresh = fed.filterKeys { !progress.told(index, it) }
        fresh.forEach { (item, step) -> player.tell(words.leftOut(index + 1, bank.amount(item), item, step + 1)) }
        if (fresh.isNotEmpty()) {
            progress = progress.telling(index, fresh.keys)
            player.saveProgress(progress)
        }
        return fed.keys
    }

    private fun advance(over: StepActivity) {
        cutShort -= index
        finishTurn(over)
        pass()
        player.saveStep(index)
    }

    /**
     * On to the next step without starting one here; the index is not saved for a met step passed, since a relog
     * passes it again. A lap ending once the flow has a reason to stop is neither counted nor judged.
     */
    private fun pass() {
        current = null
        index = (index + 1) % steps.size
        if (index == 0 && stopReason == null) endLap()
    }

    /** The turn is over: its work and count go into the lap and the run, and a goal it reached is met, in one write. */
    private fun finishTurn(over: StepActivity) {
        if (over.worked()) lapWorked = true
        val before = progress
        progress = progress.counted(index, over.amountDone())
        current = null
        reached()?.let(::meet)
        if (progress != before) player.saveProgress(progress)
    }

    /** An idle lap is not counted: the flow stops instead; a counted lap may be the last the stop allows. */
    private fun endLap() {
        if (!lapWorked) {
            stopReason = StepWords.IDLE_LAP
            return
        }
        player.lapCompleted()
        lapWorked = false
        laps++
        if (stop is FlowStop.AfterLaps && laps >= stop.laps) stopReason = words.stoppedAfterLaps(stop.laps)
    }

    private fun timeUp(): Boolean = stop is FlowStop.AfterMinutes && elapsed() >= stop.minutes * MINUTE

    private fun elapsed(): Long = clock.now() - progress.startedAt

    /** The current step's goal when it is reached and not yet met, else null. */
    private fun reached(): ResolvedGoal? {
        if (index in progress.met) return null
        val goal = flow.goals.getOrNull(index) ?: return null
        val reached = when (val what = goal.goal) {
            is Goal.Level -> player.level(what.skill) >= what.level
            is Goal.Count -> progress.total(index) + (current?.amountDone() ?: 0) >= what.total
            is Goal.Time -> elapsed() >= what.minutes * MINUTE
            is Goal.Bank -> player.banked(what.items) >= what.amount
        }
        return goal.takeIf { reached }
    }

    /**
     * The current step's [goal] is met: skipped for the run, or the flow stops; every goal met stops it too when asked.
     * The caller writes the progress.
     */
    private fun meet(goal: ResolvedGoal) {
        progress = progress.meeting(index)
        if (goal.whenMet == WhenMet.STOP) {
            stopReason = words.goalStops(index + 1, goal.goal)
            return
        }
        player.tell(words.goalMet(index + 1, goal.goal))
        if (stop == FlowStop.AllGoalsMet && allGoalsMet()) stopReason = StepWords.ALL_GOALS_MET
    }

    private fun allGoalsMet(): Boolean = flow.goals.indices.all { flow.goals[it] == null || it in progress.met }

    private fun jump(activity: ReflexGuard, jump: ReflexOutcome.Jump) {
        if (index in cutShort && activity.amountDone() == 0) {
            stopReason = words.cutShort(index + 1)
            return
        }
        player.tell(words.jump(jump.number, jump.fired, jump.index + 1))
        cutShort += index
        finishTurn(activity)
        index = jump.index
        player.saveStep(index)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
