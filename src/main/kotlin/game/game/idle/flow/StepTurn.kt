package game.idle.flow

import game.idle.location.Tile

/**
 * One turn of a step, with every rule the kinds share applied once around the kind's own [work] (step behaviour
 * refactor, Maxime, 2026-10-11): what the player must have ([needs], checked against [carried] before the kind acts),
 * the [amount] its Amount asks for (the unit in progress settles, the action is broken off, the step is over), a full bag
 * on a step that [fillsBag] (its own end without an amount, else told), nothing in reach from outside the step's area (a
 * [walkBack] under the walk step's rules, its block the step's), a wait for the world shown apart from a block, and one
 * retry rule ([RetrySkip], in the kind). The count ([Tally]) starts when the turn is built, on arrival at the work spot.
 */
class StepTurn(
    val work: StepWork,
    private val amount: Int?,
    private val needs: List<StepNeeds>,
    private val carried: Carried,
    private val fillsBag: Boolean,
    private val walkBack: (Tile) -> StepWork,
) : StepActivity {

    private val tally = Tally(work.meter)
    private var walkingBack: StepWork? = null
    private var ending = false
    private var done = false
    private var worked = false
    private var blocked: Reason? = null
    private var waiting: Reason? = null
    private var underWay: UnderWay? = null
    private var said: Said? = null
    private var stopping: StepStop? = null

    /** Once the turn ends only the unit in progress holds it; before that the walk back or the kind's own rule does. */
    override fun isBusy(): Boolean {
        tally.observe()
        return if (reached() || ending) work.finishing() else (walkingBack ?: work).isBusy()
    }

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = blocked

    override fun waiting(): Reason? = waiting

    override fun underWay(): UnderWay? = underWay

    override fun stopping(): StepStop? = stopping

    override fun said(): Said? = said.also { said = null }

    override fun amountDone(): Int {
        tally.observe()
        return tally.total()
    }

    override fun worked(): Boolean = worked

    override fun endTurn() {
        ending = true
    }

    override fun act() {
        tally.observe()
        val walk = walkingBack
        when {
            reached() || ending -> end()
            walk != null -> walking(walk.act(null))
            else -> settle(precheck() ?: work.act(left()))
        }
    }

    private fun reached(): Boolean = amount != null && tally.total() >= amount

    private fun left(): Int? = amount?.let { it - tally.total() }

    private fun precheck(): StepOutcome? =
        Preconditions.check(needs, carried)?.let { StepOutcome.Blocked(it) }
            ?: if (fillsBag && carried.bagFull()) StepOutcome.BagFull(emptyList()) else null

    private fun settle(outcome: StepOutcome): Unit =
        when (outcome) {
            StepOutcome.Worked -> startedWork()
            StepOutcome.Approached -> clear()
            is StepOutcome.OnTheWay -> onTheWay(outcome.underWay)
            is StepOutcome.Away -> away(outcome.to)
            is StepOutcome.Waiting -> wait(outcome.reason)
            is StepOutcome.Blocked -> block(outcome.reason)
            is StepOutcome.Empty -> finish()
            is StepOutcome.BagFull -> bagFull(outcome.left)
            StepOutcome.Finished -> finish()
            is StepOutcome.Stuck -> stuck(outcome)
        }

    private fun end() {
        (walkingBack ?: work).interrupt()
        finish()
    }

    private fun startedWork() {
        worked = true
        clear()
    }

    private fun clear() {
        blocked = null
        waiting = null
        underWay = null
    }

    private fun onTheWay(doing: UnderWay) {
        clear()
        underWay = doing
    }

    private fun wait(reason: Reason) {
        clear()
        waiting = reason
    }

    private fun block(reason: Reason) {
        clear()
        blocked = reason
    }

    private fun finish() {
        clear()
        done = true
    }

    /**
     * The walk back starts in the same decision and holds the turn until it arrives (the kind looks again at the next
     * decision) or until the player, standing where it cannot get on from, moves: its block is the step's.
     */
    private fun away(to: Tile) {
        val walk = walkBack(to)
        walkingBack = walk
        walking(walk.act(null))
    }

    private fun walking(outcome: StepOutcome): Unit =
        when (outcome) {
            StepOutcome.Finished -> arrived()
            is StepOutcome.Blocked -> block(outcome.reason)
            is StepOutcome.OnTheWay -> onTheWay(outcome.underWay)
            else -> clear()
        }

    private fun arrived() {
        walkingBack = null
        clear()
    }

    /**
     * A full bag ends the step (Maxime, 2026-10-11): silently when it is the step's own end, a step that fills the bag
     * with no amount; told when the step leaves something it was told to take, an amount short or items left lying.
     */
    private fun bagFull(left: List<Int>) {
        if (amount != null || left.isNotEmpty()) said = Said.Ended(Reason.BagFull(left))
        finish()
    }

    private fun stuck(stuck: StepOutcome.Stuck) {
        if (stuck.stopsFlow) {
            stopping = StepStop.Stuck(stuck.reason)
        } else {
            said = Said.Skipped(stuck.reason)
            finish()
        }
    }
}
