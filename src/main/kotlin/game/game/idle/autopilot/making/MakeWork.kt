package game.idle.autopilot.making

import game.idle.autopilot.making.MakeDecision.Choose
import game.idle.autopilot.making.MakeDecision.Settled
import game.idle.autopilot.making.MakeDecision.Use
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork

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

    /** Breaks off the making in progress. */
    fun interrupt()
}

/**
 * What the make step knows when it decides: the slots of the two items to combine (null when one is missing),
 * whether a make window is open and, if it offers the product, at which option.
 */
data class MakeView(val useSlot: Int?, val onSlot: Int?, val windowOpen: Boolean, val productOption: Int?)

sealed interface MakeDecision {

    data class Use(val useSlot: Int, val onSlot: Int) : MakeDecision

    /** Answer the open window with [option], [times] of it. */
    data class Choose(val option: Int, val times: Int) : MakeDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : MakeDecision
}

/**
 * Combine the two items and answer the make window with the product, as many as the Amount still asks for ([left]) or
 * a full bag's worth without one (Luna stops once an ingredient runs out); with an item missing the step has nothing to
 * work with. The recipe's tools and level are the shared turn's to check ([game.idle.flow.StepTurn]).
 */
object MakePlanner {

    /** A full bag's worth. */
    private const val ALL = 28

    fun decide(view: MakeView, left: Int?): MakeDecision =
        when {
            view.productOption != null -> Choose(view.productOption, left ?: ALL)
            view.windowOpen -> Settled(StepOutcome.Blocked(Reason.WrongWindow))
            view.useSlot == null || view.onSlot == null -> Settled(StepOutcome.Empty)
            else -> Use(view.useSlot, view.onSlot)
        }
}

/**
 * The make step's work: carries out [MakePlanner] decisions, the products made the count. Combining
 * right after a combine with nothing made since, and no window opened in between, means the product cannot be made, so
 * the step blocks instead of trying forever; a window answered or refused in between starts over. A block of the turn's
 * own (a tool or level, [game.idle.flow.StepTurn]) is no decision of this work's, so it does not.
 */
class MakeWork(private val maker: Maker) : StepWork {

    /** The products held at the last combine, while no other decision came since; null otherwise. */
    private var productsAtUse: Int? = null

    override val meter: Meter = Meter.Rises(maker::products)

    override fun isBusy(): Boolean = maker.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = maker.interrupt()

    override fun act(left: Int?): StepOutcome = carryOut(MakePlanner.decide(maker.look(), left))

    private fun carryOut(decision: MakeDecision): StepOutcome =
        when (decision) {
            is Use -> use(decision.useSlot, decision.onSlot)
            is Choose -> choose(decision.option, decision.times)
            is Settled -> settled(decision.outcome)
        }

    private fun use(useSlot: Int, onSlot: Int): StepOutcome {
        val products = maker.products()
        if (productsAtUse == products) return StepOutcome.Blocked(Reason.CannotMake)
        productsAtUse = products
        maker.use(useSlot, onSlot)
        return StepOutcome.Worked
    }

    private fun choose(option: Int, times: Int): StepOutcome {
        productsAtUse = null
        maker.choose(option, times)
        return StepOutcome.Worked
    }

    private fun settled(outcome: StepOutcome): StepOutcome {
        productsAtUse = null
        return outcome
    }
}
