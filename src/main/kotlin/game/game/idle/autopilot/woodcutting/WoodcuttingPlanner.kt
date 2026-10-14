package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Settled
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.StepOutcome
import game.idle.flow.Target

/** What the chop step sees when it decides: whether the player is inside the step's area, and its trees in reach. */
data class WoodcuttingView(val atLocation: Boolean, val trees: List<Candidate>)

sealed interface WoodcuttingDecision {

    /** A decision aimed at one tree. */
    sealed interface OnTree : WoodcuttingDecision, Aimed {
        val tree: Candidate

        override val aim: Aim get() = tree.aim

        override val distance: Int get() = tree.distance
    }

    data class Chop(override val tree: Candidate) : OnTree

    data class WalkTo(override val tree: Candidate) : OnTree

    data object WalkToLocation : WoodcuttingDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : WoodcuttingDecision
}

/**
 * Cut the nearest tree of the step's kind, one in reach first, leaving out the trees [game.idle.flow.RetrySkip] set
 * aside, and walk back to the work spot when nothing is in reach from outside it. The axe, the level and a full bag are
 * the shared turn's to check ([game.idle.flow.StepTurn]); with every tree a stump the step waits for one to grow back
 * (Maxime, 2026-10-11).
 */
object WoodcuttingPlanner {

    fun decide(view: WoodcuttingView, skipped: Set<Aim>): WoodcuttingDecision {
        val best = view.trees.filterNot { it.aim in skipped }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.noneInReach(Target.TREE, setAside = view.trees.isNotEmpty()))
            best.usableFromHere -> Chop(best)
            else -> WalkTo(best)
        }
    }
}
