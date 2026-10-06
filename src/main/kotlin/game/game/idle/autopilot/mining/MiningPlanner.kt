package game.idle.autopilot.mining

import game.idle.autopilot.mining.MiningDecision.Blocked
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.skill.mining.Ore
import io.luna.game.model.Position

/** A rock of the step's ore the player can walk to: [distance] walking steps to [approach]. */
data class RockCandidate(
    val objectId: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
)

/** What the mine step knows about the player when it decides. [rocks] hold the step's ore. */
data class MiningView(
    val miningLevel: Int,
    val hasUsablePickaxe: Boolean,
    val inventoryFull: Boolean,
    val atLocation: Boolean,
    val rocks: List<RockCandidate>,
)

sealed interface MiningDecision {

    /** A decision aimed at one rock. */
    sealed interface OnRock : MiningDecision {
        val rock: RockCandidate
    }

    data class Mine(override val rock: RockCandidate) : OnRock

    data class WalkTo(override val rock: RockCandidate) : OnRock

    data object WalkToLocation : MiningDecision

    data class Blocked(val reason: MiningBlockedReason) : MiningDecision
}

enum class MiningBlockedReason(val message: String) {
    NO_PICKAXE("Autopilot: you need a pickaxe that you have the Mining level to use."),
    LEVEL_TOO_LOW("Autopilot: your Mining level is too low for that ore."),
    INVENTORY_FULL("Autopilot: your inventory is full. Put a 'drop' or 'bank nearest' step after the mine step."),
    NO_ROCK("Autopilot: there is no rock with that ore you can mine here."),
}

/**
 * Mine the nearest rock of the step's ore, one in reach first, and walk back to the work spot when none is in reach
 * from outside it. A full inventory is the step's end ([MiningActivity]); here it only blocks.
 */
object MiningPlanner {

    private val preferredFirst: Comparator<RockCandidate> =
        compareByDescending<RockCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: MiningView, ore: Ore): MiningDecision {
        val best = view.rocks.minWithOrNull(preferredFirst)
        return when {
            !view.hasUsablePickaxe -> Blocked(MiningBlockedReason.NO_PICKAXE)
            view.miningLevel < ore.level -> Blocked(MiningBlockedReason.LEVEL_TOO_LOW)
            view.inventoryFull -> Blocked(MiningBlockedReason.INVENTORY_FULL)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(MiningBlockedReason.NO_ROCK)
            best.usableFromHere -> Mine(best)
            else -> WalkTo(best)
        }
    }
}
