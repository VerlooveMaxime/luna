package game.idle.autopilot.making

import game.idle.autopilot.making.MakeDecision.Blocked
import game.idle.autopilot.making.MakeDecision.Choose
import game.idle.autopilot.making.MakeDecision.Done
import game.idle.autopilot.making.MakeDecision.Use
import game.idle.flow.StepActivity

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

    fun tell(message: String)
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

    data class Blocked(val reason: MakeBlockedReason) : MakeDecision
}

enum class MakeBlockedReason {
    /** Nothing to combine when the step starts. */
    NO_INGREDIENTS,

    /** The items were combined but nothing came of it: a level too low, or Luna refused. */
    CANNOT_MAKE,

    /** A make window is open that does not offer the product. */
    WRONG_WINDOW,
}

/**
 * Combine the two items, answer the make window with the product, done once one of the items runs out after making
 * some; with nothing to combine from the start, the step waits instead, so a flow never spins through empty steps.
 */
object MakePlanner {

    fun decide(view: MakeView, madeSome: Boolean): MakeDecision =
        when {
            view.productOption != null -> Choose(view.productOption)
            view.windowOpen -> Blocked(MakeBlockedReason.WRONG_WINDOW)
            view.useSlot == null || view.onSlot == null -> if (madeSome) Done else Blocked(MakeBlockedReason.NO_INGREDIENTS)
            else -> Use(view.useSlot, view.onSlot)
        }
}

/**
 * The make step: makes [recipe]'s product until [amount] were made or, without an amount, until an ingredient runs
 * out. Making that reaches the amount is stopped at once. Combining twice in a row with nothing made and no window in
 * between means it cannot be made, and the step says so instead of trying forever.
 */
class MakeActivity(private val maker: Maker, private val recipe: Recipe, private val amount: Int? = null) : StepActivity {

    private var productsAtStart: Int? = null
    private var productsAtLastUse = 0
    private var lastDecision: MakeDecision? = null
    private var done = false

    override fun isBusy(): Boolean = maker.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun act() {
        val start = productsAtStart ?: maker.products().also { productsAtStart = it }
        if (amountReached()) {
            maker.stop()
            done = true
            return
        }
        lastDecision = carryOut(MakePlanner.decide(maker.look(), madeSome = maker.products() > start), start)
    }

    private fun amountReached(): Boolean {
        val start = productsAtStart ?: return false
        return amount != null && maker.products() - start >= amount
    }

    /** What was done: a second fruitless use blocks instead. */
    private fun carryOut(decision: MakeDecision, start: Int): MakeDecision = when (decision) {
        is Use -> if (fruitless() && maker.products() == productsAtLastUse) {
            tellOnce(Blocked(MakeBlockedReason.CANNOT_MAKE))
        } else {
            decision.also {
                productsAtLastUse = maker.products()
                maker.use(it.useSlot, it.onSlot)
            }
        }
        is Choose -> decision.also { maker.choose(it.option, times(start)) }
        Done -> decision.also { done = true }
        is Blocked -> tellOnce(decision)
    }

    /** The last use made nothing, or the step already found it cannot make the product and nothing changed since. */
    private fun fruitless(): Boolean = lastDecision is Use || lastDecision == Blocked(MakeBlockedReason.CANNOT_MAKE)

    /** As many as are left to make, or a full inventory's worth: Luna stops once an ingredient runs out. */
    private fun times(start: Int): Int = amount?.let { it - (maker.products() - start) } ?: ALL

    private fun tellOnce(decision: Blocked): Blocked {
        if (decision != lastDecision) maker.tell(message(decision.reason))
        return decision
    }

    private fun message(reason: MakeBlockedReason): String = when (reason) {
        MakeBlockedReason.NO_INGREDIENTS -> "Autopilot: you have nothing to make ${recipe.name} with."
        MakeBlockedReason.CANNOT_MAKE -> "Autopilot: you cannot make ${recipe.name} yet."
        MakeBlockedReason.WRONG_WINDOW -> "Autopilot: this window does not make ${recipe.name}."
    }

    private companion object {
        const val ALL = 28
    }
}
