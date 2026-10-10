package game.idle.autopilot.cooking

import game.idle.autopilot.cooking.CookingDecision.Blocked
import game.idle.autopilot.cooking.CookingDecision.CookAll
import game.idle.autopilot.cooking.CookingDecision.Done
import game.idle.autopilot.cooking.CookingDecision.UseOn
import game.idle.autopilot.cooking.CookingDecision.WalkTo
import game.idle.autopilot.cooking.CookingDecision.WalkToLocation
import game.idle.autopilot.PlaceCandidate
import game.idle.flow.CountsAmount
import game.idle.flow.StepActivity
import io.luna.game.model.Position

/** What the cook step can see and do for one player. [LunaCooker] is the in-game one. */
interface Cooker {

    fun isBusy(): Boolean

    fun look(): CookingView

    /** Uses the raw food in [slot] on [place], which opens the cooking window. */
    fun useOn(place: PlaceCandidate, slot: Int)

    /** Picks "cook all" in the open cooking window. */
    fun cookAll()

    fun walkTo(place: PlaceCandidate)

    fun walkToLocation()

    /** How much of the step's raw food the player carries. */
    fun raw(): Int

    /** Stops the cooking in progress. */
    fun stop()
}

/** What the cook step knows when it decides: [rawSlot] holds some of the step's raw food, if any is left. */
data class CookingView(
    val rawSlot: Int?,
    val windowOpen: Boolean,
    val atLocation: Boolean,
    val places: List<PlaceCandidate>,
)

sealed interface CookingDecision {

    /** A decision aimed at one place. */
    sealed interface OnPlace : CookingDecision {
        val place: PlaceCandidate
    }

    data class UseOn(override val place: PlaceCandidate, val slot: Int) : OnPlace

    data class WalkTo(override val place: PlaceCandidate) : OnPlace

    data object CookAll : CookingDecision

    data object WalkToLocation : CookingDecision

    data object Done : CookingDecision

    data class Blocked(val reason: CookingBlockedReason) : CookingDecision
}

enum class CookingBlockedReason(val message: String) {
    NO_FIRE("Autopilot: there is no fire or range you can reach here. Put a 'light' step before the cook step."),
    NOTHING_TO_COOK("Autopilot: you have nothing to cook. Put a fish or make step before the cook step."),
}

/**
 * Cook on the nearest fire or range, one in reach first; done once the raw food runs out after cooking some. With
 * nothing to cook from the start the step waits instead, so a flow never spins through empty steps.
 */
object CookingPlanner {

    private val preferredFirst: Comparator<PlaceCandidate> =
        compareByDescending<PlaceCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: CookingView, cookedSome: Boolean): CookingDecision {
        val best = view.places.minWithOrNull(preferredFirst)
        return when {
            view.rawSlot == null -> if (cookedSome) Done else Blocked(CookingBlockedReason.NOTHING_TO_COOK)
            view.windowOpen -> CookAll
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(CookingBlockedReason.NO_FIRE)
            best.usableFromHere -> UseOn(best, view.rawSlot)
            else -> WalkTo(best)
        }
    }
}

/**
 * The cook step: cooks the step's raw food until [amount] were used (cooked or burnt) or, without an amount, until
 * none is left. Cooking that reaches the amount is stopped at once. A place that gets the same decision twice in a
 * row (used without the window opening, or walked to but still out of reach; fires burn out) is skipped for as long
 * as the step runs.
 */
class CookingActivity(private val cooker: Cooker, private val amount: Int? = null) : StepActivity, CountsAmount {

    private val skippedPlaces = mutableSetOf<Position>()
    private var lastDecision: CookingDecision? = null
    private var rawAtStart: Int? = null
    private var done = false

    override fun isBusy(): Boolean = cooker.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): String? = (lastDecision as? Blocked)?.reason?.message

    override fun act() {
        val start = rawAtStart ?: cooker.raw().also { rawAtStart = it }
        if (amountReached()) {
            cooker.stop()
            done = true
            return
        }
        val decision = decideSkippingRetries(cooker.look(), cookedSome = cooker.raw() < start)
        carryOut(decision)
        lastDecision = decision
    }

    override fun amountDone(): Int = rawAtStart?.let { it - cooker.raw() } ?: 0

    private fun amountReached(): Boolean = amount != null && amountDone() >= amount

    private fun carryOut(decision: CookingDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is UseOn -> cooker.useOn(decision.place, decision.slot)
        is WalkTo -> cooker.walkTo(decision.place)
        CookAll -> cooker.cookAll()
        WalkToLocation -> cooker.walkToLocation()
        Done -> done = true
    }

    private fun decideSkippingRetries(view: CookingView, cookedSome: Boolean): CookingDecision {
        val decision = decide(view, cookedSome)
        val retried = retriedPlace(decision) ?: return decision
        skippedPlaces += retried.position
        return decide(view, cookedSome)
    }

    private fun decide(view: CookingView, cookedSome: Boolean): CookingDecision =
        CookingPlanner.decide(view.copy(places = view.places.filterNot { it.position in skippedPlaces }), cookedSome)

    /** The place [decision] aims at when the previous decision was the same step on the same place. */
    private fun retriedPlace(decision: CookingDecision): PlaceCandidate? {
        val attempt = decision as? CookingDecision.OnPlace ?: return null
        val last = lastDecision as? CookingDecision.OnPlace ?: return null
        return attempt.place.takeIf { attempt::class == last::class && it.position == last.place.position }
    }
}
