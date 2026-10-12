package game.idle.flow

import game.idle.location.Tile

/** What one decision of a kind's [StepWork] came to; [StepTurn] applies the shared rule for each. */
sealed interface StepOutcome {

    /** The step's own work started, or the click that starts it: a chop, a cast, raw food used on a fire, a drop, a rest. */
    data object Worked : StepOutcome

    /** A walk to a target or a free tile, the bank opened or closed: not work yet, not a problem either. */
    data object Approached : StepOutcome

    /** Nothing in reach from outside the step's area: the walk back goes [to] the area's anchor. */
    data class Away(val to: Tile) : StepOutcome

    /** The world will fix it: no tree, rock, spot or npc standing in reach for now. */
    data class Waiting(val reason: Reason) : StepOutcome

    /** The player or the flow must fix it. */
    data class Blocked(val reason: Reason) : StepOutcome

    /**
     * Nothing to work with from the flow: none of its input left in the bag, or not enough for one more unit (one ore of
     * the two a bar takes, four bars for a five-bar item).
     */
    data object Empty : StepOutcome

    /** The bag is full; [left] are the item ids a pick-up step left lying, none for a kind that fills the bag itself. */
    data class BagFull(val left: List<Int>) : StepOutcome

    /** The step's own end: arrived, healed, nothing left to drop. */
    data object Finished : StepOutcome

    /** A bank step with something to do that cannot do it: the flow stops, or the step skips its turn (S07b's If stuck). */
    data class Stuck(val reason: Reason.Stuck, val stopsFlow: Boolean) : StepOutcome

    companion object {

        /**
         * Nothing of [target] is left in reach inside the step's area: a wait for the world to supply one (a stump grows
         * back), unless every one there was [setAside] by the retry rule, which the player must look into (a tree Luna
         * cannot reach), so a block (Maxime, 2026-10-11).
         */
        fun noneInReach(target: Target, setAside: Boolean): StepOutcome =
            if (setAside) Blocked(Reason.NoneInReach(target)) else Waiting(Reason.NoneInReach(target))
    }
}
