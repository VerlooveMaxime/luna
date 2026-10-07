package game.idle.autopilot.fighting

import game.idle.autopilot.fighting.FightDecision.Attack
import game.idle.autopilot.fighting.FightDecision.Blocked
import game.idle.autopilot.fighting.FightDecision.Eat
import game.idle.autopilot.fighting.FightDecision.Flee
import game.idle.autopilot.fighting.FightDecision.OutOfFood
import game.idle.autopilot.fighting.FightDecision.WalkTo
import game.idle.autopilot.fighting.FightDecision.WalkToLocation
import game.idle.flow.StepActivity
import io.luna.game.model.Position

/** What the fight step can see and do for one player. [LunaFighter] is the in-game one. */
interface Fighter {

    /** Walking up to a target, or fighting one that is still alive. */
    fun isBusy(): Boolean

    fun health(): Health

    fun look(): FightView

    fun attack(target: TargetCandidate)

    /** Walks to the tile [target] can be hit from. */
    fun walkTo(target: TargetCandidate)

    fun eat(slot: Int)

    /** Runs from whatever attacks the player; false when there is nowhere to run. */
    fun flee(): Boolean

    fun walkToLocation()

    /** How many of the step's npcs the player has killed since the step began. */
    fun kills(): Int

    /** Stops the fighting in progress. */
    fun stop()

    fun tell(message: String)
}

data class Health(val hitpoints: Int, val full: Int) {

    fun below(percent: Int): Boolean = hitpoints * 100 < full * percent
}

/** An npc the player could fight, ranked like fishing spots: [distance] walking steps to [approach], where it can be hit from. */
data class TargetCandidate(
    val npcIndex: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
)

/** What the fight step knows about the player when it decides. */
data class FightView(
    val health: Health,
    /** The inventory slot of the first food the player carries, null when none. */
    val foodSlot: Int?,
    /** Npcs fighting the player that can get at them. */
    val threats: Int,
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

    data class Eat(val slot: Int) : FightDecision

    data class Flee(val health: Health) : FightDecision

    /** Below the barrier with no food and nothing left to run from: the flow stops. */
    data class OutOfFood(val health: Health) : FightDecision

    data object WalkToLocation : FightDecision

    data class Blocked(val reason: FightBlockedReason) : FightDecision
}

enum class FightBlockedReason(val message: String) {
    NO_TARGET("Autopilot: there is nothing to fight here that you can reach."),
}

/**
 * Eat below the barrier, run when there is no food, otherwise fight the nearest npc, one in reach first, walking up
 * to it when none is: Luna's click does not walk.
 */
object FightingPlanner {

    private val preferredFirst: Comparator<TargetCandidate> =
        compareByDescending<TargetCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: FightView, eatBelow: Int): FightDecision {
        val low = view.health.below(eatBelow)
        val best = view.targets.minWithOrNull(preferredFirst)
        val food = view.foodSlot
        return when {
            low && food != null -> Eat(food)
            low && view.threats > 0 -> Flee(view.health)
            low -> OutOfFood(view.health)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(FightBlockedReason.NO_TARGET)
            best.usableFromHere -> Attack(best)
            else -> WalkTo(best)
        }
    }

    fun outOfFood(health: Health): String =
        "Autopilot: stopped, out of food at ${health.hitpoints}/${health.full} hitpoints."
}

/**
 * The fight step: fights until [amount] npcs were killed or, without an amount, until the flow is stopped. Below
 * [eatBelow] % of full hitpoints it eats, even mid-fight; with no food left it runs from what attacks the player
 * and then stops the flow, or stops it at once when there is nowhere to run. An npc attacked or walked to twice in a
 * row on the same tile with no kill in between (out of reach after all, or taken by someone else) is skipped until the
 * next kill, or until every npc around was skipped: npcs wander, so one out of reach now may not be later.
 */
class FightingActivity(private val fighter: Fighter, private val eatBelow: Int, private val amount: Int? = null) : StepActivity {

    private val skippedTargets = mutableSetOf<Int>()
    private var lastDecision: FightDecision? = null
    private var killsAtLastAttack = 0
    private var stopReason: String? = null
    private var done = false

    override fun isBusy(): Boolean = fighter.isBusy() && !amountReached() && !fighter.health().below(eatBelow)

    override fun isDone(): Boolean = done

    override fun stopReason(): String? = stopReason

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
        is Attack -> {
            killsAtLastAttack = fighter.kills()
            fighter.attack(decision.target)
        }
        is WalkTo -> {
            killsAtLastAttack = fighter.kills()
            fighter.walkTo(decision.target)
        }
        is Eat -> fighter.eat(decision.slot)
        is Flee -> flee(decision.health)
        is OutOfFood -> stopReason = FightingPlanner.outOfFood(decision.health)
        WalkToLocation -> fighter.walkToLocation()
        is Blocked -> if (decision != lastDecision) fighter.tell(decision.reason.message) else Unit
    }

    private fun flee(health: Health) {
        if (!fighter.flee()) stopReason = FightingPlanner.outOfFood(health)
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
        FightingPlanner.decide(view.copy(targets = view.targets.filterNot { it.npcIndex in skippedTargets }), eatBelow)

    /** The npc [decision] aims at when the previous decision did the same to it, on the same tile, and nothing died since. */
    private fun retriedTarget(decision: FightDecision): TargetCandidate? {
        val attempt = decision as? FightDecision.OnTarget ?: return null
        val last = lastDecision as? FightDecision.OnTarget ?: return null
        return attempt.target.takeIf { attempt::class == last::class && it.sameAs(last.target) && fighter.kills() == killsAtLastAttack }
    }

    private fun TargetCandidate.sameAs(other: TargetCandidate): Boolean = npcIndex == other.npcIndex && position == other.position
}
