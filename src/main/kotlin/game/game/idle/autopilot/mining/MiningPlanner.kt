package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.Settled
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.StepOutcome
import game.idle.flow.Target

/** What the mine step sees when it decides: whether the player is inside the step's area, and the step's rocks in reach. */
data class MiningView(val atLocation: Boolean, val rocks: List<Candidate>)

sealed interface MiningDecision {

    /** A decision aimed at one rock. */
    sealed interface OnRock : MiningDecision, Aimed {
        val rock: Candidate

        override val aim: Aim get() = rock.aim

        override val distance: Int get() = rock.distance
    }

    data class Mine(override val rock: Candidate) : OnRock

    data class WalkTo(override val rock: Candidate) : OnRock

    data object WalkToLocation : MiningDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : MiningDecision
}

/**
 * Mine the nearest rock of the step's ore, one in reach first, leaving out the rocks [game.idle.flow.RetrySkip] set aside, and walk
 * back to the work spot when none is in reach from outside it. The pickaxe, the level and a full bag are the shared
 * turn's to check ([game.idle.flow.StepTurn]); with every rock empty the step waits for one to fill again.
 */
object MiningPlanner {

    fun decide(view: MiningView, skipped: Set<Aim>): MiningDecision {
        val best = view.rocks.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.noneInReach(Target.ROCK, setAside = view.rocks.isNotEmpty()))
            best.usableFromHere -> Mine(best)
            else -> WalkTo(best)
        }
    }
}
