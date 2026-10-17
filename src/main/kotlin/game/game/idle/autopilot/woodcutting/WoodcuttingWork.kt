package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Settled
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.location.Tile

/** What the chop step can see and do for one player. [LunaWoodcutter] is the in-game one. */
interface Woodcutter {

    fun isBusy(): Boolean

    fun look(): WoodcuttingView

    fun chop(tree: Candidate)

    fun walkTo(tree: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many logs of the step's kind the player carries. */
    fun logs(): Int

    /** Breaks off the chop in progress. */
    fun interrupt()
}

/** The chop step's work: carries out [WoodcuttingPlanner] decisions, its logs the count. */
class WoodcuttingWork(private val woodcutter: Woodcutter) : StepWork {

    private val retry = RetrySkip.forTheTurn(woodcutter::logs)

    override val meter: Meter = Meter.Rises(woodcutter::logs)

    override fun isBusy(): Boolean = woodcutter.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = woodcutter.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = woodcutter.look()
        return carryOut(retry.decide { skipped -> WoodcuttingPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: WoodcuttingDecision): StepOutcome =
        when (decision) {
            is Chop -> chop(decision.tree)
            is WalkTo -> walkTo(decision.tree)
            WalkToLocation -> StepOutcome.Away(woodcutter.anchor())
            is Settled -> decision.outcome
        }

    private fun chop(tree: Candidate): StepOutcome {
        woodcutter.chop(tree)
        return StepOutcome.Worked
    }

    private fun walkTo(tree: Candidate): StepOutcome {
        woodcutter.walkTo(tree)
        return tree.approached
    }
}
