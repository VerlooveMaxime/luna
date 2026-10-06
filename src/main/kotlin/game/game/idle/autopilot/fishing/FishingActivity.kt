package game.idle.autopilot.fishing

import game.idle.autopilot.fishing.FishingDecision.Blocked
import game.idle.autopilot.fishing.FishingDecision.Fish
import game.idle.autopilot.fishing.FishingDecision.WalkTo
import game.idle.autopilot.fishing.FishingDecision.WalkToLocation
import game.idle.flow.StepActivity
import io.luna.game.model.Position

/** What the fish step can see and do for one player. [LunaFisher] is the in-game one. */
interface Fisher {

    fun isBusy(): Boolean

    fun look(): FishingView

    fun fish(spot: SpotCandidate)

    fun walkTo(spot: SpotCandidate)

    fun walkToLocation()

    /** How many of the step's catches the player carries. */
    fun catches(): Int

    /** Stops the fishing in progress. */
    fun stop()

    fun tell(message: String)
}

/** A fishing spot the player can walk to, ranked like trees: [distance] walking steps to [approach]. */
data class SpotCandidate(
    val npcIndex: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
)

/** What the fish step knows about the player when it decides. */
data class FishingView(
    val hasTool: Boolean,
    val inventoryFull: Boolean,
    val atLocation: Boolean,
    val spots: List<SpotCandidate>,
)

sealed interface FishingDecision {

    /** A decision aimed at one spot. */
    sealed interface OnSpot : FishingDecision {
        val spot: SpotCandidate
    }

    data class Fish(override val spot: SpotCandidate) : OnSpot

    data class WalkTo(override val spot: SpotCandidate) : OnSpot

    data object WalkToLocation : FishingDecision

    data class Blocked(val reason: FishingBlockedReason) : FishingDecision
}

enum class FishingBlockedReason(val message: String) {
    NO_TOOL("Autopilot: you need the right fishing tool for this spot."),
    INVENTORY_FULL("Autopilot: your inventory is full. Put a 'drop' or 'bank nearest' step after the fish step."),
    NO_SPOT("Autopilot: there is no fishing spot you can reach here."),
}

/** Fish at the nearest spot, one in reach first, and walk back to the work spot when none is in reach from outside it. */
object FishingPlanner {

    private val preferredFirst: Comparator<SpotCandidate> =
        compareByDescending<SpotCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: FishingView): FishingDecision {
        val best = view.spots.minWithOrNull(preferredFirst)
        return when {
            !view.hasTool -> Blocked(FishingBlockedReason.NO_TOOL)
            view.inventoryFull -> Blocked(FishingBlockedReason.INVENTORY_FULL)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(FishingBlockedReason.NO_SPOT)
            best.usableFromHere -> Fish(best)
            else -> WalkTo(best)
        }
    }
}

/**
 * The fish step: fishes until [amount] fish were caught or, without an amount, until the inventory fills after at
 * least one cast (a full inventory at the start blocks). Fishing that reaches the amount is stopped at once. A spot
 * that gets the same decision twice in a row (fished without a catch filling the inventory, or walked to but still
 * out of reach; spots move) is skipped for as long as the step runs.
 */
class FishingActivity(private val fisher: Fisher, private val amount: Int? = null) : StepActivity {

    private val skippedSpots = mutableSetOf<Position>()
    private var lastDecision: FishingDecision? = null
    private var catchesAtStart: Int? = null
    private var fished = false
    private var done = false

    override fun isBusy(): Boolean = fisher.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun act() {
        if (catchesAtStart == null) catchesAtStart = fisher.catches()
        if (amountReached()) {
            fisher.stop()
            done = true
            return
        }
        val view = fisher.look()
        if (fished && view.inventoryFull) {
            done = true
            return
        }
        val decision = decideSkippingRetries(view)
        carryOut(decision)
        lastDecision = decision
    }

    private fun amountReached(): Boolean {
        val start = catchesAtStart ?: return false
        return amount != null && fisher.catches() - start >= amount
    }

    private fun carryOut(decision: FishingDecision) = when (decision) {
        is Fish -> {
            fished = true
            fisher.fish(decision.spot)
        }
        is WalkTo -> fisher.walkTo(decision.spot)
        WalkToLocation -> fisher.walkToLocation()
        is Blocked -> if (decision != lastDecision) fisher.tell(decision.reason.message) else Unit
    }

    private fun decideSkippingRetries(view: FishingView): FishingDecision {
        val decision = decide(view)
        val retried = retriedSpot(decision) ?: return decision
        skippedSpots += retried.position
        return decide(view)
    }

    private fun decide(view: FishingView): FishingDecision =
        FishingPlanner.decide(view.copy(spots = view.spots.filterNot { it.position in skippedSpots }))

    /** The spot [decision] aims at when the previous decision was the same step on the same spot. */
    private fun retriedSpot(decision: FishingDecision): SpotCandidate? {
        val attempt = decision as? FishingDecision.OnSpot ?: return null
        val last = lastDecision as? FishingDecision.OnSpot ?: return null
        return attempt.spot.takeIf { attempt::class == last::class && it.position == last.spot.position }
    }
}
