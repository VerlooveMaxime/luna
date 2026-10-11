package game.idle.autopilot.fighting

import game.idle.autopilot.fighting.FightDecision.Attack
import game.idle.autopilot.fighting.FightDecision.Blocked
import game.idle.autopilot.fighting.FightDecision.WalkTo
import game.idle.autopilot.fighting.FightDecision.WalkToLocation
import game.idle.flow.CountsAmount
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.LegacyActivity
import game.idle.flow.StepStop
import game.idle.flow.Target
import io.luna.game.model.Position

/** What the fight step can see and do for one player. [LunaFighter] is the in-game one. */
interface Fighter {

    /** Walking up to a target, or fighting one that is still alive. */
    fun isBusy(): Boolean

    fun look(): FightView

    fun attack(target: TargetCandidate)

    /** Walks to the tile [target] can be hit from. */
    fun walkTo(target: TargetCandidate)

    fun walkToLocation()

    /** How many of the step's npcs the player has killed since the step began. */
    fun kills(): Int

    /** Whether the npc killed last is still dying: its drop lands on the ground when its death is over. */
    fun lastKillDying(): Boolean

    /** Stops the fighting in progress. */
    fun stop()
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
)

/** What the fight step knows about the player when it decides. */
data class FightView(
    val atLocation: Boolean,
    val targets: List<TargetCandidate>,
)

sealed interface FightDecision {

    /** A decision aimed at one npc. */
    sealed interface OnTarget : FightDecision {
        val target: TargetCandidate
    }

    data class Attack(override val target: TargetCandidate) : OnTarget

    data class WalkTo(override val target: TargetCandidate) : OnTarget

    data object WalkToLocation : FightDecision

    data class Blocked(val reason: Reason) : FightDecision
}

/**
 * Fights back what attacks the player first (Maxime, 2026-10-10: in single combat nothing else can be fought, in multi
 * a second npc would join), then the nearest npc, one in reach first, walking up to it when none is: Luna's click does
 * not walk.
 */
object FightingPlanner {

    private val preferredFirst: Comparator<TargetCandidate> =
        compareByDescending<TargetCandidate> { it.attacking }
            .thenByDescending { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: FightView): FightDecision {
        val best = view.targets.minWithOrNull(preferredFirst)
        return when {
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(Reason.NoneInReach(Target.NPC))
            best.usableFromHere -> Attack(best)
            else -> WalkTo(best)
        }
    }
}

/**
 * The fight step: fights until [amount] npcs were killed or, without an amount, until the flow is stopped; the flow's
 * reflexes eat and run (S07c). The kill that reaches [amount] ends the step once its death is over, so a pick-up step
 * after it finds the drop (S07d). An npc attacked or walked to twice in a row on the same tile with no kill in between
 * (out of reach after all, or taken by someone else) is skipped until the next kill, or until every npc around was
 * skipped: npcs wander, so one out of reach now may not be later.
 */
class FightingActivity(private val fighter: Fighter, private val amount: Int? = null) : LegacyActivity, CountsAmount {

    private val skippedTargets = mutableSetOf<Int>()
    private var lastDecision: FightDecision? = null
    private var killsAtLastAttack = 0
    private var done = false

    override fun isBusy(): Boolean = if (amountReached()) fighter.lastKillDying() else fighter.isBusy()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = (lastDecision as? Blocked)?.reason

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

    override fun amountDone(): Int = fighter.kills()

    override fun act() {
        if (amountReached()) {
            fighter.stop()
            done = true
            return
        }
        val view = fighter.look()
        if (fighter.kills() > killsAtLastAttack) skippedTargets.clear()
        val decision = decideSkippingRetries(view)
        carryOut(decision)
        lastDecision = decision
    }

    private fun amountReached(): Boolean = amount != null && fighter.kills() >= amount

    private fun carryOut(decision: FightDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is Attack -> {
            killsAtLastAttack = fighter.kills()
            fighter.attack(decision.target)
        }
        is WalkTo -> {
            killsAtLastAttack = fighter.kills()
            fighter.walkTo(decision.target)
        }
        WalkToLocation -> fighter.walkToLocation()
    }

    private fun decideSkippingRetries(view: FightView): FightDecision {
        val decision = decide(view)
        val retried = retriedTarget(decision) ?: return decision
        skippedTargets += retried.npcIndex
        val next = decide(view)
        if (next is FightDecision.OnTarget) return next
        skippedTargets.clear()
        return decide(view)
    }

    private fun decide(view: FightView): FightDecision =
        FightingPlanner.decide(view.copy(targets = view.targets.filterNot { it.npcIndex in skippedTargets }))

    /** The npc [decision] aims at when the previous decision did the same to it, on the same tile, and nothing died since. */
    private fun retriedTarget(decision: FightDecision): TargetCandidate? {
        val attempt = decision as? FightDecision.OnTarget ?: return null
        val last = lastDecision as? FightDecision.OnTarget ?: return null
        return attempt.target.takeIf { attempt::class == last::class && it.sameAs(last.target) && fighter.kills() == killsAtLastAttack }
    }

    private fun TargetCandidate.sameAs(other: TargetCandidate): Boolean = npcIndex == other.npcIndex && position == other.position
}
