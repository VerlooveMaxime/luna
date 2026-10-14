package game.idle.autopilot.pickup

import game.idle.autopilot.Candidate
import game.idle.autopilot.pickup.PickUpDecision.Settled
import game.idle.autopilot.pickup.PickUpDecision.Take
import game.idle.autopilot.pickup.PickUpDecision.WalkTo
import game.idle.autopilot.pickup.PickUpDecision.WalkToLocation
import game.idle.flow.Aim
import game.idle.flow.Aimed
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork

/** What the pick-up step can see and do for one player. [LunaPicker] is the in-game one. */
interface Picker {

    /** Walking to an item, or about to take one. */
    fun isBusy(): Boolean

    fun look(): PickUpView

    /** Takes [item] from the tile the player stands on. */
    fun take(item: Candidate)

    /** Walks onto [item]'s tile. */
    fun walkTo(item: Candidate)

    fun walkToLocation()

    /** How many items the player took off the ground since the step began, a stack counting one (Maxime, 2026-10-11). */
    fun pickedUp(): Int

    /** Breaks off the take or the walk in progress. */
    fun interrupt()
}

/** What the pick-up step knows when it decides: whether the player is in the area, and the items it can walk onto there. */
data class PickUpView(val atLocation: Boolean, val items: List<Candidate>)

sealed interface PickUpDecision {

    /** A decision aimed at one item. */
    sealed interface OnItem : PickUpDecision, Aimed {
        val item: Candidate

        override val aim: Aim get() = item.aim

        override val distance: Int get() = item.distance
    }

    data class Take(override val item: Candidate) : OnItem

    data class WalkTo(override val item: Candidate) : OnItem

    data object WalkToLocation : PickUpDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : PickUpDecision
}

/**
 * The nearest item that fits the bag by walking, taken from its tile, leaving out the items [RetrySkip] set aside; with
 * items left that none fits, the bag is full and the step names them; with none, the step walks to the area when it is
 * not there, else it is over: it never waits (Maxime, 2026-10-10).
 */
object PickUpPlanner {

    fun decide(view: PickUpView, skipped: Set<Aim>): PickUpDecision {
        val lying = view.items.filterNot { it.aim in skipped }
        val best = lying.filter { it.fits }.minWithOrNull(Candidate.NEAREST_FIRST)
        return when {
            best != null && best.usableFromHere -> Take(best)
            best != null -> WalkTo(best)
            lying.isNotEmpty() -> Settled(StepOutcome.BagFull(lying.map { it.id }.distinct()))
            !view.atLocation -> WalkToLocation
            else -> Settled(StepOutcome.Finished)
        }
    }
}

/**
 * The pick-up step's work (S07d): takes the items, its pickups the count, until none is left lying. An item aimed at
 * twice in a row with nothing picked up in between (out of reach after all, taken by someone else) is set aside for the
 * rest of the turn. Away from the area it walks there once, then ends; it is never walked back (Maxime, 2026-10-11).
 */
class PickUpWork(private val picker: Picker) : StepWork {

    private val retry = RetrySkip.forTheTurn(picker::pickedUp)
    private var walkedToLocation = false

    override val meter: Meter = Meter.Rises(picker::pickedUp)

    override fun isBusy(): Boolean = picker.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = picker.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = picker.look()
        return carryOut(retry.decide { skipped -> PickUpPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: PickUpDecision): StepOutcome =
        when (decision) {
            is Take -> take(decision.item)
            is WalkTo -> walkTo(decision.item)
            WalkToLocation -> walkToLocationOnce()
            is Settled -> decision.outcome
        }

    private fun take(item: Candidate): StepOutcome {
        picker.take(item)
        return StepOutcome.Worked
    }

    private fun walkTo(item: Candidate): StepOutcome {
        picker.walkTo(item)
        return StepOutcome.Approached
    }

    /** Still away after its one walk to the area, the step is over. */
    private fun walkToLocationOnce(): StepOutcome {
        if (walkedToLocation) return StepOutcome.Finished
        walkedToLocation = true
        picker.walkToLocation()
        return StepOutcome.Approached
    }
}
