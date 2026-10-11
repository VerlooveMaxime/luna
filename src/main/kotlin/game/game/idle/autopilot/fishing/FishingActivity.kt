package game.idle.autopilot.fishing

import game.idle.autopilot.fishing.FishingDecision.Blocked
import game.idle.autopilot.fishing.FishingDecision.Fish
import game.idle.autopilot.fishing.FishingDecision.WalkTo
import game.idle.autopilot.fishing.FishingDecision.WalkToLocation
import game.idle.flow.CountsAmount
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepActivity
import game.idle.flow.StepStop
import game.idle.flow.Target
import game.idle.flow.ToolNeed
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill

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
}

/** A fishing spot the player can walk to, ranked like trees: [distance] walking steps to [approach]. */
data class SpotCandidate(
    val npcIndex: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
)

/**
 * What a fishing method needs, as the step blocks on them: its [tool], its [bait] when it takes any (S03b: bait like
 * tools), and the Fishing [level] of the tool.
 */
data class FishingNeeds(val tool: ToolNeed, val bait: ToolNeed?, val level: Int) {

    companion object {
        fun of(method: FishingMethod): FishingNeeds {
            val tool = method.tool
            val bait = tool.bait?.let { ToolNeed(word = null, mapOf(it to 1), countable = false) }
            return FishingNeeds(ToolNeed(word = null, mapOf(tool.id to 1)), bait, tool.level)
        }
    }
}

/** What the fish step knows about the player when it decides. */
data class FishingView(
    val hasTool: Boolean,
    val hasLevel: Boolean,
    val hasBait: Boolean,
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

    data class Blocked(val reason: Reason) : FishingDecision
}

/** Fish at the nearest spot, one in reach first, and walk back to the work spot when none is in reach from outside it. */
object FishingPlanner {

    private val preferredFirst: Comparator<SpotCandidate> =
        compareByDescending<SpotCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    /**
     * [needs] are the method's, named when the view lacks one of them; the view of a method without bait always has
     * bait (`LunaFisher`), so the bait arm reads the need to know whether there is one to name.
     */
    fun decide(view: FishingView, needs: FishingNeeds): FishingDecision {
        val best = view.spots.minWithOrNull(preferredFirst)
        val bait = needs.bait
        return when {
            !view.hasTool -> Blocked(Reason.NeedsTool(needs.tool))
            !view.hasLevel -> Blocked(Reason.NeedsLevel(Skill.FISHING, needs.level))
            bait != null && !view.hasBait -> Blocked(Reason.NeedsTool(bait))
            view.inventoryFull -> Blocked(Reason.BagFull(emptyList()))
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(Reason.NoneInReach(Target.FISHING_SPOT))
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
class FishingActivity(private val fisher: Fisher, private val needs: FishingNeeds, private val amount: Int? = null) : StepActivity, CountsAmount {

    private val skippedSpots = mutableSetOf<Position>()
    private var lastDecision: FishingDecision? = null
    private var catchesAtStart: Int? = null
    private var fished = false
    private var done = false

    override fun isBusy(): Boolean = fisher.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = (lastDecision as? Blocked)?.reason

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

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

    override fun amountDone(): Int = catchesAtStart?.let { fisher.catches() - it } ?: 0

    private fun amountReached(): Boolean = amount != null && amountDone() >= amount

    private fun carryOut(decision: FishingDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is Fish -> {
            fished = true
            fisher.fish(decision.spot)
        }
        is WalkTo -> fisher.walkTo(decision.spot)
        WalkToLocation -> fisher.walkToLocation()
    }

    private fun decideSkippingRetries(view: FishingView): FishingDecision {
        val decision = decide(view)
        val retried = retriedSpot(decision) ?: return decision
        skippedSpots += retried.position
        return decide(view)
    }

    private fun decide(view: FishingView): FishingDecision =
        FishingPlanner.decide(view.copy(spots = view.spots.filterNot { it.position in skippedSpots }), needs)

    /** The spot [decision] aims at when the previous decision was the same step on the same spot. */
    private fun retriedSpot(decision: FishingDecision): SpotCandidate? {
        val attempt = decision as? FishingDecision.OnSpot ?: return null
        val last = lastDecision as? FishingDecision.OnSpot ?: return null
        return attempt.spot.takeIf { attempt::class == last::class && it.position == last.spot.position }
    }
}
