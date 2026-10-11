package game.idle.autopilot.making

import game.idle.autopilot.making.MakeDecision.Blocked
import game.idle.autopilot.making.MakeDecision.Choose
import game.idle.autopilot.making.MakeDecision.Done
import game.idle.autopilot.making.MakeDecision.Use
import game.idle.flow.CountsAmount
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepActivity
import game.idle.flow.StepStop
import game.idle.flow.Supply

/** What the make step can see and do for one player. [LunaMaker] is the in-game one. */
interface Maker {

    fun isBusy(): Boolean

    fun look(): MakeView

    /** Uses the item in [useSlot] on the one in [onSlot]. */
    fun use(useSlot: Int, onSlot: Int)

    /** Picks option [index] of the open make window, to make it [times] times. */
    fun choose(index: Int, times: Int)

    /** How many of the product the player carries. */
    fun products(): Int

    /** Stops the making in progress. */
    fun stop()
}

/**
 * What the make step knows when it decides: the slots of the two items to combine (null when one is missing),
 * whether a make window is open and, if it offers the product, at which option.
 */
data class MakeView(val useSlot: Int?, val onSlot: Int?, val windowOpen: Boolean, val productOption: Int?)

sealed interface MakeDecision {

    data class Use(val useSlot: Int, val onSlot: Int) : MakeDecision

    data class Choose(val option: Int) : MakeDecision

    data object Done : MakeDecision

    data class Blocked(val reason: Reason) : MakeDecision
}

/**
 * Combine the two items, answer the make window with the product, done once one of the items runs out after making
 * some; with nothing to combine from the start, the step waits instead, so a flow never spins through empty steps.
 */
object MakePlanner {

    fun decide(view: MakeView, madeSome: Boolean): MakeDecision =
        when {
            view.productOption != null -> Choose(view.productOption)
            view.windowOpen -> Blocked(Reason.WrongWindow)
            view.useSlot == null || view.onSlot == null -> if (madeSome) Done else Blocked(Reason.NothingToUse(Supply.INGREDIENTS))
            else -> Use(view.useSlot, view.onSlot)
        }
}

/**
 * The make step: makes the recipe's product until [amount] were made or, without an amount, until an ingredient runs
 * out. Making that reaches the amount is stopped at once. Combining twice in a row with nothing made and no window in
 * between means it cannot be made, and the step blocks instead of trying forever.
 */
class MakeActivity(private val maker: Maker, private val amount: Int? = null) : StepActivity, CountsAmount {

    private var productsAtStart: Int? = null
    private var productsAtLastUse = 0
    private var lastDecision: MakeDecision? = null
    private var done = false

    override fun isBusy(): Boolean = maker.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = (lastDecision as? Blocked)?.reason

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

    override fun act() {
        val start = productsAtStart ?: maker.products().also { productsAtStart = it }
        if (amountReached()) {
            maker.stop()
            done = true
            return
        }
        lastDecision = carryOut(MakePlanner.decide(maker.look(), madeSome = maker.products() > start), start)
    }

    override fun amountDone(): Int = productsAtStart?.let { maker.products() - it } ?: 0

    private fun amountReached(): Boolean = amount != null && amountDone() >= amount

    /** What was done: a second fruitless use blocks instead. */
    private fun carryOut(decision: MakeDecision, start: Int): MakeDecision = when (decision) {
        is Use -> if (fruitless() && maker.products() == productsAtLastUse) {
            Blocked(Reason.CannotMake)
        } else {
            decision.also {
                productsAtLastUse = maker.products()
                maker.use(it.useSlot, it.onSlot)
            }
        }
        is Choose -> decision.also { maker.choose(it.option, times(start)) }
        Done -> decision.also { done = true }
        is Blocked -> decision
    }

    /** The last use made nothing, or the step already found it cannot make the product and nothing changed since. */
    private fun fruitless(): Boolean = lastDecision is Use || lastDecision == Blocked(Reason.CannotMake)

    /** As many as are left to make, or a full inventory's worth: Luna stops once an ingredient runs out. */
    private fun times(start: Int): Int = amount?.let { it - (maker.products() - start) } ?: ALL

    private companion object {
        const val ALL = 28
    }
}
