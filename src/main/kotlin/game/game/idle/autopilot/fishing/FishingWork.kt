package game.idle.autopilot.fishing

import game.idle.autopilot.Candidate
import game.idle.autopilot.fishing.FishingDecision.Fish
import game.idle.autopilot.fishing.FishingDecision.Settled
import game.idle.autopilot.fishing.FishingDecision.WalkTo
import game.idle.autopilot.fishing.FishingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target
import game.idle.location.Tile

/** What the fish step can see and do for one player. [LunaFisher] is the in-game one. */
interface Fisher {

    fun isBusy(): Boolean

    fun look(): FishingView

    fun fish(spot: Candidate)

    fun walkTo(spot: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many of the step's catches the player carries. */
    fun catches(): Int

    /** Breaks off the fishing in progress. */
    fun interrupt()
}

/** What the fish step sees when it decides: whether the player is inside the step's area, and the method's spots in reach. */
data class FishingView(val atLocation: Boolean, val spots: List<Candidate>)

sealed interface FishingDecision {

    /** A decision aimed at one spot. */
    sealed interface OnSpot : FishingDecision, Aimed {
        val spot: Candidate

        override val aim: Aim get() = spot.aim

        override val distance: Int get() = spot.distance
    }

    data class Fish(override val spot: Candidate) : OnSpot

    data class WalkTo(override val spot: Candidate) : OnSpot

    data object WalkToLocation : FishingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : FishingDecision
}

/**
 * Fish at the nearest spot, one in reach first, leaving out the spots [game.idle.flow.RetrySkip] set aside, and walk back to the work
 * spot when none is in reach from outside it. The tool, the bait, the level and a full bag are the shared turn's to
 * check ([game.idle.flow.StepTurn]); with no spot in reach the step waits for one to come back.
 */
object FishingPlanner {

    fun decide(view: FishingView, skipped: Set<Aim>): FishingDecision {
        val best = view.spots.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.noneInReach(Target.FISHING_SPOT, setAside = view.spots.isNotEmpty()))
            best.usableFromHere -> Fish(best)
            else -> WalkTo(best)
        }
    }
}

/** The fish step's work: carries out [FishingPlanner] decisions, its catches the count. */
class FishingWork(private val fisher: Fisher) : StepWork {

    private val retry = RetrySkip.forTheTurn(fisher::catches)

    override val meter: Meter = Meter.Rises(fisher::catches)

    override fun isBusy(): Boolean = fisher.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = fisher.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = fisher.look()
        return carryOut(retry.decide { skipped -> FishingPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: FishingDecision): StepOutcome =
        when (decision) {
            is Fish -> fish(decision.spot)
            is WalkTo -> walkTo(decision.spot)
            WalkToLocation -> StepOutcome.Away(fisher.anchor())
            is Settled -> decision.outcome
        }

    private fun fish(spot: Candidate): StepOutcome {
        fisher.fish(spot)
        return StepOutcome.Worked
    }

    private fun walkTo(spot: Candidate): StepOutcome {
        fisher.walkTo(spot)
        return StepOutcome.Approached
    }
}
