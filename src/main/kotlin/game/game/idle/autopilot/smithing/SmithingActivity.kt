package game.idle.autopilot.smithing

import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.smithing.SmithingDecision.Blocked
import game.idle.autopilot.smithing.SmithingDecision.Choose
import game.idle.autopilot.smithing.SmithingDecision.Done
import game.idle.autopilot.smithing.SmithingDecision.OnAnvil
import game.idle.autopilot.smithing.SmithingDecision.UseOn
import game.idle.autopilot.smithing.SmithingDecision.WalkTo
import game.idle.autopilot.smithing.SmithingDecision.WalkToLocation
import game.idle.flow.CountsAmount
import game.idle.flow.StepActivity
import io.luna.game.model.Position

/** What the smith step can see and do for one player. [LunaSmither] is the in-game one. */
interface Smither {

    fun isBusy(): Boolean

    fun look(): SmithingView

    /** Uses the bar in [slot] on [anvil], which opens the smithing window. */
    fun useOn(anvil: PlaceCandidate, slot: Int)

    /** Picks the step's item in the open smithing window, [times] of it (1, 5 or 10, the window's options). */
    fun choose(times: Int)

    fun walkTo(anvil: PlaceCandidate)

    fun walkToLocation()

    /** How many of the step's item the player carries. */
    fun made(): Int

    /** Stops the smithing in progress. */
    fun stop()
}

/** What the smith step knows when it decides: [barSlot] holds the bar, while there are enough for one item. */
data class SmithingView(
    val barSlot: Int?,
    val hasHammer: Boolean,
    val smithingLevel: Int,
    val windowOpen: Boolean,
    val atLocation: Boolean,
    val anvils: List<PlaceCandidate>,
)

sealed interface SmithingDecision {

    /** A decision aimed at one anvil. */
    sealed interface OnAnvil : SmithingDecision {
        val anvil: PlaceCandidate
    }

    data class UseOn(override val anvil: PlaceCandidate, val slot: Int) : OnAnvil

    data class WalkTo(override val anvil: PlaceCandidate) : OnAnvil

    data class Choose(val times: Int) : SmithingDecision

    data object WalkToLocation : SmithingDecision

    data object Done : SmithingDecision

    data class Blocked(val reason: SmithingBlockedReason) : SmithingDecision
}

enum class SmithingBlockedReason(val message: String) {
    NOTHING_TO_SMITH("Autopilot: you have no bars for that item. Put a smelt step before the smith step."),
    NO_HAMMER("Autopilot: you need a hammer in your inventory to smith."),
    LEVEL_TOO_LOW("Autopilot: your Smithing level is too low for that item."),
    NO_ANVIL("Autopilot: there is no anvil you can reach here."),
}

/**
 * Smith at the nearest anvil, one in reach first, and pick the item once the window is open; done once the bars run
 * out after smithing some. With no bars from the start the step waits instead.
 */
object SmithingPlanner {

    /** The smithing window's counts, largest first. */
    private val OPTIONS = listOf(10, 5, 1)

    private val preferredFirst: Comparator<PlaceCandidate> =
        compareByDescending<PlaceCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: SmithingView, level: Int, madeSome: Boolean, times: Int): SmithingDecision {
        val best = view.anvils.minWithOrNull(preferredFirst)
        return when {
            view.barSlot == null -> if (madeSome) Done else Blocked(SmithingBlockedReason.NOTHING_TO_SMITH)
            !view.hasHammer -> Blocked(SmithingBlockedReason.NO_HAMMER)
            view.smithingLevel < level -> Blocked(SmithingBlockedReason.LEVEL_TOO_LOW)
            view.windowOpen -> Choose(times)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(SmithingBlockedReason.NO_ANVIL)
            best.usableFromHere -> UseOn(best, view.barSlot)
            else -> WalkTo(best)
        }
    }

    /** The largest window count that does not pass [remaining]; the largest of all when there is no amount. */
    fun times(remaining: Int?): Int = if (remaining == null) OPTIONS.first() else OPTIONS.first { it <= remaining }
}

/**
 * The smith step: smiths the step's item until [amount] were made or, without an amount, until the bars run out. The
 * window's count is the largest that does not pass what is left to make; smithing that reaches the amount is stopped
 * at once. An anvil that gets the same decision twice in a row with nothing made in between (used without the window
 * opening, or walked to but still out of reach) is skipped for as long as the step runs.
 */
class SmithingActivity(private val smither: Smither, private val level: Int, private val amount: Int? = null) : StepActivity, CountsAmount {

    private val skippedAnvils = mutableSetOf<Position>()
    private var lastDecision: SmithingDecision? = null
    private var madeAtLastDecision = 0
    private var madeAtStart: Int? = null
    private var done = false

    override fun isBusy(): Boolean = smither.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): String? = (lastDecision as? Blocked)?.reason?.message

    override fun act() {
        val start = madeAtStart ?: smither.made().also { madeAtStart = it }
        if (amountReached()) {
            smither.stop()
            done = true
            return
        }
        val made = smither.made() - start
        val decision = decideSkippingRetries(smither.look(), madeSome = made > 0, SmithingPlanner.times(amount?.minus(made)))
        carryOut(decision)
        lastDecision = decision
        madeAtLastDecision = smither.made()
    }

    override fun amountDone(): Int = madeAtStart?.let { smither.made() - it } ?: 0

    private fun amountReached(): Boolean = amount != null && amountDone() >= amount

    private fun carryOut(decision: SmithingDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is UseOn -> smither.useOn(decision.anvil, decision.slot)
        is WalkTo -> smither.walkTo(decision.anvil)
        is Choose -> smither.choose(decision.times)
        WalkToLocation -> smither.walkToLocation()
        Done -> done = true
    }

    private fun decideSkippingRetries(view: SmithingView, madeSome: Boolean, times: Int): SmithingDecision {
        val decision = decide(view, madeSome, times)
        val retried = retriedAnvil(decision) ?: return decision
        skippedAnvils += retried.position
        return decide(view, madeSome, times)
    }

    private fun decide(view: SmithingView, madeSome: Boolean, times: Int): SmithingDecision =
        SmithingPlanner.decide(view.copy(anvils = view.anvils.filterNot { it.position in skippedAnvils }), level, madeSome, times)

    /** The anvil [decision] aims at when the previous decision was the same step on it and nothing came of it. */
    private fun retriedAnvil(decision: SmithingDecision): PlaceCandidate? {
        val attempt = decision as? OnAnvil ?: return null
        val last = lastDecision as? OnAnvil ?: return null
        val nothingMade = smither.made() == madeAtLastDecision
        return attempt.anvil.takeIf { nothingMade && attempt::class == last::class && it.position == last.anvil.position }
    }
}
