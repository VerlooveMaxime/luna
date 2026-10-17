package game.idle.autopilot.smelting

import game.idle.autopilot.Candidate
import game.idle.autopilot.smelting.SmeltingDecision.Settled
import game.idle.autopilot.smelting.SmeltingDecision.Smelt
import game.idle.autopilot.smelting.SmeltingDecision.WalkTo
import game.idle.autopilot.smelting.SmeltingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target
import game.idle.location.Tile

/** What the smelt step can see and do for one player. [LunaSmelter] is the in-game one. */
interface Smelter {

    fun isBusy(): Boolean

    fun look(): SmeltingView

    /** Uses the ore in [slot] on [furnace], which smelts every bar the ores carried make. */
    fun smelt(furnace: Candidate, slot: Int)

    fun walkTo(furnace: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many of the step's bars the player carries. */
    fun bars(): Int

    /** Breaks off the smelting in progress. */
    fun interrupt()
}

/** What the smelt step knows when it decides: [oreSlot] holds the ore to use, while there is ore for a bar. */
data class SmeltingView(val oreSlot: Int?, val atLocation: Boolean, val furnaces: List<Candidate>)

sealed interface SmeltingDecision {

    /** A decision aimed at one furnace. */
    sealed interface OnFurnace : SmeltingDecision, Aimed {
        val furnace: Candidate

        override val aim: Aim get() = furnace.aim

        override val distance: Int get() = furnace.distance
    }

    data class Smelt(override val furnace: Candidate, val slot: Int) : OnFurnace

    data class WalkTo(override val furnace: Candidate) : OnFurnace

    data object WalkToLocation : SmeltingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : SmeltingDecision
}

/**
 * Smelt at the nearest furnace, one in reach first, leaving out the furnaces [RetrySkip] set aside, and walk back to the
 * work spot when none is in reach from outside it; with no ore left for a bar the step has nothing to work with. The
 * level is the shared turn's to check ([game.idle.flow.StepTurn]).
 */
object SmeltingPlanner {

    fun decide(view: SmeltingView, skipped: Set<Aim>): SmeltingDecision {
        val best = view.furnaces.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            view.oreSlot == null -> Settled(StepOutcome.Empty)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.Blocked(Reason.NoneInReach(Target.FURNACE)))
            best.usableFromHere -> Smelt(best, view.oreSlot)
            else -> WalkTo(best)
        }
    }
}

/** The smelt step's work: carries out [SmeltingPlanner] decisions, the bars made the count. */
class SmeltingWork(private val smelter: Smelter) : StepWork {

    private val retry = RetrySkip.forTheTurn(smelter::bars)

    override val meter: Meter = Meter.Rises(smelter::bars)

    override fun isBusy(): Boolean = smelter.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = smelter.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = smelter.look()
        return carryOut(retry.decide { skipped -> SmeltingPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: SmeltingDecision): StepOutcome =
        when (decision) {
            is Smelt -> smelt(decision.furnace, decision.slot)
            is WalkTo -> walkTo(decision.furnace)
            WalkToLocation -> StepOutcome.Away(smelter.anchor())
            is Settled -> decision.outcome
        }

    private fun smelt(furnace: Candidate, slot: Int): StepOutcome {
        smelter.smelt(furnace, slot)
        return StepOutcome.Worked
    }

    private fun walkTo(furnace: Candidate): StepOutcome {
        smelter.walkTo(furnace)
        return furnace.approached
    }
}
