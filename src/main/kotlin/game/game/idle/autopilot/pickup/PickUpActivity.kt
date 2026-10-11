package game.idle.autopilot.pickup

import game.idle.autopilot.pickup.PickUpDecision.BagFull
import game.idle.autopilot.pickup.PickUpDecision.Done
import game.idle.autopilot.pickup.PickUpDecision.Take
import game.idle.autopilot.pickup.PickUpDecision.WalkTo
import game.idle.autopilot.pickup.PickUpDecision.WalkToLocation
import game.idle.flow.CountsAmount
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.LegacyActivity
import game.idle.flow.StepStop
import io.luna.game.model.Position

/** What the pick-up step can see and do for one player. [LunaPicker] is the in-game one. */
interface Picker {

    /** Walking to an item, or about to take one. */
    fun isBusy(): Boolean

    fun look(): PickUpView

    /** Takes [item] from the tile the player stands on. */
    fun take(item: GroundLoot)

    /** Walks onto [item]'s tile. */
    fun walkTo(item: GroundLoot)

    fun walkToLocation()

    /** How many items the player took off the ground since the step began, a stack counting one (Maxime, 2026-10-11). */
    fun pickedUp(): Int
}

/** An item the step could take: [distance] walking steps to its tile, 0 on it; whether it [fits] the bag. */
data class GroundLoot(val id: Int, val position: Position, val distance: Int, val fits: Boolean)

/** What the pick-up step knows when it decides: whether the player is in the area, and the items it can walk onto there. */
data class PickUpView(val atLocation: Boolean, val items: List<GroundLoot>)

sealed interface PickUpDecision {

    /** A decision aimed at one item. */
    sealed interface OnItem : PickUpDecision {
        val item: GroundLoot
    }

    data class Take(override val item: GroundLoot) : OnItem

    data class WalkTo(override val item: GroundLoot) : OnItem

    data object WalkToLocation : PickUpDecision

    /** Items lie there but none fits the bag: [left] are their ids. */
    data class BagFull(val left: List<Int>) : PickUpDecision

    data object Done : PickUpDecision
}

/**
 * The nearest item that fits the bag by walking, taken from its tile; with items left that none fits, the bag is full;
 * with none, the step walks to the area when it is not there, else it is done: it never waits (Maxime, 2026-10-10).
 */
object PickUpPlanner {

    private val nearestFirst: Comparator<GroundLoot> =
        compareBy<GroundLoot> { it.distance }.thenBy { it.position.x }.thenBy { it.position.y }

    fun decide(view: PickUpView): PickUpDecision {
        val best = view.items.filter { it.fits }.minWithOrNull(nearestFirst)
        return when {
            best != null && best.distance == 0 -> Take(best)
            best != null -> WalkTo(best)
            view.items.isNotEmpty() -> BagFull(view.items.map { it.id }.distinct())
            !view.atLocation -> WalkToLocation
            else -> Done
        }
    }
}

/**
 * The pick-up step (S07d): takes the items until [amount] pickups or, without one, until none is left, then ends. An item
 * aimed at twice in a row with nothing picked up in between (out of reach after all, taken by someone else) is skipped
 * for the rest of the step, and the walk to the area is made once. A full bag ends the step, which says what it left
 * (Maxime, 2026-10-11).
 */
class PickUpActivity(private val picker: Picker, private val amount: Int?) : LegacyActivity, CountsAmount {

    private val skipped = mutableSetOf<Pair<Int, Position>>()
    private var lastDecision: PickUpDecision? = null
    private var pickedAtLastTry = 0
    private var walkedToLocation = false
    private var done = false
    private var said: Said? = null

    override fun isBusy(): Boolean = picker.isBusy()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = null

    override fun stopping(): StepStop? = null

    override fun said(): Said? = said.also { said = null }

    override fun amountDone(): Int = picker.pickedUp()

    override fun act() {
        if (amount != null && picker.pickedUp() >= amount) {
            done = true
            return
        }
        val decision = decideSkippingRetries(picker.look())
        carryOut(decision)
        lastDecision = decision
    }

    private fun carryOut(decision: PickUpDecision) = when (decision) {
        is Take -> {
            pickedAtLastTry = picker.pickedUp()
            picker.take(decision.item)
        }
        is WalkTo -> {
            pickedAtLastTry = picker.pickedUp()
            picker.walkTo(decision.item)
        }
        WalkToLocation -> walkToLocation()
        is BagFull -> bagFull(decision.left)
        Done -> finish()
    }

    private fun walkToLocation() {
        if (walkedToLocation) {
            finish()
        } else {
            walkedToLocation = true
            picker.walkToLocation()
        }
    }

    private fun bagFull(left: List<Int>) {
        said = Said.Ended(Reason.BagFull(left))
        finish()
    }

    private fun finish() {
        done = true
    }

    private fun decideSkippingRetries(view: PickUpView): PickUpDecision {
        val decision = decide(view)
        val retried = retriedItem(decision) ?: return decision
        skipped += retried.id to retried.position
        return decide(view)
    }

    private fun decide(view: PickUpView): PickUpDecision =
        PickUpPlanner.decide(view.copy(items = view.items.filterNot { it.id to it.position in skipped }))

    /** The item [decision] aims at when the previous decision was the same and nothing was picked up since. */
    private fun retriedItem(decision: PickUpDecision): GroundLoot? =
        (decision as? PickUpDecision.OnItem)?.item?.takeIf { decision == lastDecision && picker.pickedUp() == pickedAtLastTry }
}
