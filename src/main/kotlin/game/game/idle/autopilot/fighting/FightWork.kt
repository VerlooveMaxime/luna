package game.idle.autopilot.fighting

import game.idle.autopilot.fighting.FightDecision.Attack
import game.idle.autopilot.fighting.FightDecision.Settled
import game.idle.autopilot.fighting.FightDecision.WalkTo
import game.idle.autopilot.fighting.FightDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target
import game.idle.location.Tile
import io.luna.game.model.Position

/** What the fight step can see and do for one player. [LunaFighter] is the in-game one. */
interface Fighter {

    /** Walking up to a target, or fighting one that is still alive. */
    fun isBusy(): Boolean

    fun look(): FightView

    fun attack(target: TargetCandidate)

    /** Walks to the tile [target] can be hit from. */
    fun walkTo(target: TargetCandidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many of the step's npcs the player has killed since the step began. */
    fun kills(): Int

    /** Whether the npc killed last is still dying: its drop lands on the ground when its death is over. */
    fun lastKillDying(): Boolean

    /** Breaks off the fighting in progress. */
    fun interrupt()
}

/**
 * An npc the player could fight, ranked like fishing spots: [distance] walking steps to [approach], where it can be hit
 * from; [attacking] when it attacks the player.
 */
data class TargetCandidate(
    val npcIndex: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
    val attacking: Boolean = false,
) {
    val aim: Aim get() = Aim(npcIndex, Tile.of(position))
}

/** What the fight step knows about the player when it decides. */
data class FightView(
    val atLocation: Boolean,
    val targets: List<TargetCandidate>,
)

sealed interface FightDecision {

    /** A decision aimed at one npc. */
    sealed interface OnTarget : FightDecision, Aimed {
        val target: TargetCandidate

        override val aim: Aim get() = target.aim

        override val distance: Int get() = target.distance
    }

    data class Attack(override val target: TargetCandidate) : OnTarget

    data class WalkTo(override val target: TargetCandidate) : OnTarget

    data object WalkToLocation : FightDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : FightDecision
}

/**
 * Fights back what attacks the player first (Maxime, 2026-10-10: in single combat nothing else can be fought, in multi
 * a second npc would join), then the nearest npc, one in reach first, walking up to it when none is: Luna's click does
 * not walk. The npcs [RetrySkip] set aside are left out by index: a set-aside npc that wanders is still set aside. With
 * none in reach inside the area the step waits: the world respawns npcs, and the ones set aside come back (Maxime,
 * 2026-10-11).
 */
object FightingPlanner {

    private val preferredFirst: Comparator<TargetCandidate> =
        compareByDescending<TargetCandidate> { it.attacking }
            .thenByDescending { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: FightView, skipped: Set<Aim>): FightDecision {
        val setAside = skipped.map { it.key }.toSet()
        val best = view.targets.filterNot { it.npcIndex in setAside }.minWithOrNull(preferredFirst)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Settled(StepOutcome.Waiting(Reason.NoneInReach(Target.NPC)))
            best.usableFromHere -> Attack(best)
            else -> WalkTo(best)
        }
    }
}

/**
 * The fight step's work: carries out [FightingPlanner] decisions, the kills its count, retrying by
 * [RetrySkip.untilProgress]. The kill that reaches the Amount ends the step only once its death is over ([finishing]),
 * so a pick-up step after it finds the drop (S07d).
 */
class FightWork(private val fighter: Fighter) : StepWork {

    private val retry = RetrySkip.untilProgress(fighter::kills)

    override val meter: Meter = Meter.Rises(fighter::kills)

    override fun isBusy(): Boolean = fighter.isBusy()

    override fun finishing(): Boolean = fighter.lastKillDying()

    override fun interrupt() = fighter.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = fighter.look()
        return carryOut(retry.decide { skipped -> FightingPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: FightDecision): StepOutcome =
        when (decision) {
            is Attack -> attack(decision.target)
            is WalkTo -> walkTo(decision.target)
            WalkToLocation -> StepOutcome.Away(fighter.anchor())
            is Settled -> decision.outcome
        }

    private fun attack(target: TargetCandidate): StepOutcome {
        fighter.attack(target)
        return StepOutcome.Worked
    }

    private fun walkTo(target: TargetCandidate): StepOutcome {
        fighter.walkTo(target)
        return StepOutcome.Approached
    }
}
