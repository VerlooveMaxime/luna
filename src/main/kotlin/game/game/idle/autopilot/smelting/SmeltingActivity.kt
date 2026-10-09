package game.idle.autopilot.smelting

import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.smelting.SmeltingDecision.Blocked
import game.idle.autopilot.smelting.SmeltingDecision.Done
import game.idle.autopilot.smelting.SmeltingDecision.OnFurnace
import game.idle.autopilot.smelting.SmeltingDecision.Smelt
import game.idle.autopilot.smelting.SmeltingDecision.WalkTo
import game.idle.autopilot.smelting.SmeltingDecision.WalkToLocation
import game.idle.flow.StepActivity
import game.skill.smithing.BarType
import io.luna.game.model.Position

/** What the smelt step can see and do for one player. [LunaSmelter] is the in-game one. */
interface Smelter {

    fun isBusy(): Boolean

    fun look(): SmeltingView

    /** Uses the ore in [slot] on [furnace], which smelts every bar the ores carried make. */
    fun smelt(furnace: PlaceCandidate, slot: Int)

    fun walkTo(furnace: PlaceCandidate)

    fun walkToLocation()

    /** How many of the step's bars the player carries. */
    fun bars(): Int

    /** Stops the smelting in progress. */
    fun stop()
}

/** What the smelt step knows when it decides: [oreSlot] holds the ore to use, while there is ore for a bar. */
data class SmeltingView(
    val oreSlot: Int?,
    val smithingLevel: Int,
    val atLocation: Boolean,
    val furnaces: List<PlaceCandidate>,
)

sealed interface SmeltingDecision {

    /** A decision aimed at one furnace. */
    sealed interface OnFurnace : SmeltingDecision {
        val furnace: PlaceCandidate
    }

    data class Smelt(override val furnace: PlaceCandidate, val slot: Int) : OnFurnace

    data class WalkTo(override val furnace: PlaceCandidate) : OnFurnace

    data object WalkToLocation : SmeltingDecision

    data object Done : SmeltingDecision

    data class Blocked(val reason: SmeltingBlockedReason) : SmeltingDecision
}

enum class SmeltingBlockedReason(val message: String) {
    NOTHING_TO_SMELT("Autopilot: you have no ore for that bar. Put mine steps before the smelt step."),
    LEVEL_TOO_LOW("Autopilot: your Smithing level is too low for that bar."),
    NO_FURNACE("Autopilot: there is no furnace you can reach here."),
}

/**
 * Smelt at the nearest furnace, one in reach first; done once the ore runs out after smelting some. With no ore from
 * the start the step waits instead, so a flow never spins through empty steps.
 */
object SmeltingPlanner {

    private val preferredFirst: Comparator<PlaceCandidate> =
        compareByDescending<PlaceCandidate> { it.usableFromHere }
            .thenBy { it.distance }
            .thenBy { it.position.x }
            .thenBy { it.position.y }

    fun decide(view: SmeltingView, bar: BarType, smeltedSome: Boolean): SmeltingDecision {
        val best = view.furnaces.minWithOrNull(preferredFirst)
        return when {
            view.oreSlot == null -> if (smeltedSome) Done else Blocked(SmeltingBlockedReason.NOTHING_TO_SMELT)
            view.smithingLevel < bar.level -> Blocked(SmeltingBlockedReason.LEVEL_TOO_LOW)
            best == null && !view.atLocation -> WalkToLocation
            best == null -> Blocked(SmeltingBlockedReason.NO_FURNACE)
            best.usableFromHere -> Smelt(best, view.oreSlot)
            else -> WalkTo(best)
        }
    }
}

/**
 * The smelt step: smelts the step's bar until [amount] were made or, without an amount, until the ore runs out.
 * Smelting that reaches the amount is stopped at once. A furnace that gets the same decision twice in a row with no
 * bar made in between (used without smelting starting, or walked to but still out of reach) is skipped for as long
 * as the step runs.
 */
class SmeltingActivity(private val smelter: Smelter, private val bar: BarType, private val amount: Int? = null) : StepActivity {

    private val skippedFurnaces = mutableSetOf<Position>()
    private var lastDecision: SmeltingDecision? = null
    private var barsAtLastDecision = 0
    private var barsAtStart: Int? = null
    private var done = false

    override fun isBusy(): Boolean = smelter.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): String? = (lastDecision as? Blocked)?.reason?.message

    override fun act() {
        val start = barsAtStart ?: smelter.bars().also { barsAtStart = it }
        if (amountReached()) {
            smelter.stop()
            done = true
            return
        }
        val decision = decideSkippingRetries(smelter.look(), smeltedSome = smelter.bars() > start)
        carryOut(decision)
        lastDecision = decision
        barsAtLastDecision = smelter.bars()
    }

    private fun amountReached(): Boolean {
        val start = barsAtStart ?: return false
        return amount != null && smelter.bars() - start >= amount
    }

    private fun carryOut(decision: SmeltingDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is Smelt -> smelter.smelt(decision.furnace, decision.slot)
        is WalkTo -> smelter.walkTo(decision.furnace)
        WalkToLocation -> smelter.walkToLocation()
        Done -> done = true
    }

    private fun decideSkippingRetries(view: SmeltingView, smeltedSome: Boolean): SmeltingDecision {
        val decision = decide(view, smeltedSome)
        val retried = retriedFurnace(decision) ?: return decision
        skippedFurnaces += retried.position
        return decide(view, smeltedSome)
    }

    private fun decide(view: SmeltingView, smeltedSome: Boolean): SmeltingDecision =
        SmeltingPlanner.decide(view.copy(furnaces = view.furnaces.filterNot { it.position in skippedFurnaces }), bar, smeltedSome)

    /** The furnace [decision] aims at when the previous decision was the same step on it and no bar came of it. */
    private fun retriedFurnace(decision: SmeltingDecision): PlaceCandidate? {
        val attempt = decision as? OnFurnace ?: return null
        val last = lastDecision as? OnFurnace ?: return null
        val nothingMade = smelter.bars() == barsAtLastDecision
        return attempt.furnace.takeIf { nothingMade && attempt::class == last::class && it.position == last.furnace.position }
    }
}
