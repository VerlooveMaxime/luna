package game.idle.autopilot.smithing

import game.idle.autopilot.Candidate
import game.idle.autopilot.smithing.SmithingDecision.Choose
import game.idle.autopilot.smithing.SmithingDecision.Settled
import game.idle.autopilot.smithing.SmithingDecision.UseOn
import game.idle.autopilot.smithing.SmithingDecision.WalkTo
import game.idle.autopilot.smithing.SmithingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target
import game.idle.location.Tile

/** What the smith step can see and do for one player. [LunaSmither] is the in-game one. */
interface Smither {

    fun isBusy(): Boolean

    fun look(): SmithingView

    /** Uses the bar in [slot] on [anvil], which opens the smithing window. */
    fun useOn(anvil: Candidate, slot: Int)

    /** Picks the step's item in the open smithing window, [times] of it (1, 5 or 10, the window's options). */
    fun choose(times: Int)

    fun walkTo(anvil: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many of the step's item the player carries. */
    fun made(): Int

    /** Breaks off the smithing in progress. */
    fun interrupt()
}

/** What the smith step knows when it decides: [barSlot] holds the bar, while there are enough for one item. */
data class SmithingView(
    val barSlot: Int?,
    val windowOpen: Boolean,
    val atLocation: Boolean,
    val anvils: List<Candidate>,
)

sealed interface SmithingDecision {

    /** A decision aimed at one anvil. */
    sealed interface OnAnvil : SmithingDecision, Aimed {
        val anvil: Candidate

        override val aim: Aim get() = anvil.aim

        override val distance: Int get() = anvil.distance
    }

    data class UseOn(override val anvil: Candidate, val slot: Int) : OnAnvil

    data class WalkTo(override val anvil: Candidate) : OnAnvil

    data class Choose(val times: Int) : SmithingDecision

    data object WalkToLocation : SmithingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : SmithingDecision
}

/**
 * Smith at the nearest anvil, one in reach first, leaving out the anvils [RetrySkip] set aside, pick the item once the
 * window is open, and walk back to the work spot when no anvil is in reach from outside it; with too few bars left the
 * step has nothing to work with. The hammer and the level are the shared turn's to check ([game.idle.flow.StepTurn]).
 */
object SmithingPlanner {

    /** The smithing window's counts, largest first. */
    private val OPTIONS = listOf(10, 5, 1)

    /** [left] is what the Amount still asks for, null without one. */
    fun decide(view: SmithingView, skipped: Set<Aim>, left: Int?): SmithingDecision {
        val best = view.anvils.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            view.barSlot == null -> Settled(StepOutcome.Empty)
            view.windowOpen -> Choose(times(left))
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.ANVIL)))
            best.usableFromHere -> UseOn(best, view.barSlot)
            else -> WalkTo(best)
        }
    }

    /** The largest window count that does not pass [remaining]; the largest of all when there is no amount. */
    fun times(remaining: Int?): Int = if (remaining == null) OPTIONS.first() else OPTIONS.first { it <= remaining }
}

/** The smith step's work: carries out [SmithingPlanner] decisions, the items made the count. */
class SmithingWork(private val smither: Smither) : StepWork {

    private val retry = RetrySkip.forTheTurn(smither::made)

    override val meter: Meter = Meter.Rises(smither::made)

    override fun isBusy(): Boolean = smither.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = smither.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = smither.look()
        return carryOut(retry.decide { skipped -> SmithingPlanner.decide(view, skipped, left) })
    }

    private fun carryOut(decision: SmithingDecision): StepOutcome =
        when (decision) {
            is UseOn -> useOn(decision.anvil, decision.slot)
            is WalkTo -> walkTo(decision.anvil)
            is Choose -> choose(decision.times)
            WalkToLocation -> StepOutcome.Away(smither.anchor())
            is Settled -> decision.outcome
        }

    private fun useOn(anvil: Candidate, slot: Int): StepOutcome {
        smither.useOn(anvil, slot)
        return StepOutcome.Worked
    }

    private fun walkTo(anvil: Candidate): StepOutcome {
        smither.walkTo(anvil)
        return anvil.approached
    }

    private fun choose(times: Int): StepOutcome {
        smither.choose(times)
        return StepOutcome.Worked
    }
}
