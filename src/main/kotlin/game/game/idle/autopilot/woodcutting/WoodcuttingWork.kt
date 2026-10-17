package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.autopilot.travel.WalkUps
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Chop
import game.idle.autopilot.woodcutting.WoodcuttingDecision.Settled
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkTo
import game.idle.autopilot.woodcutting.WoodcuttingDecision.WalkToLocation
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.location.Tile

/** What the chop step can see and do for one player. [LunaWoodcutter] is the in-game one. */
interface Woodcutter {

    fun isBusy(): Boolean

    fun look(): WoodcuttingView

    fun chop(tree: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many logs of the step's kind the player carries. */
    fun logs(): Int

    /** Breaks off the chop in progress. */
    fun interrupt()
}

/** The chop step's work: carries out [WoodcuttingPlanner] decisions, its logs the count. */
class WoodcuttingWork(private val woodcutter: Woodcutter, private val walkUps: WalkUps) : StepWork {

    private val retry = RetrySkip.forTheTurn(woodcutter::logs, walkUps::setAside)

    override val meter: Meter = Meter.Rises(woodcutter::logs)

    override fun isBusy(): Boolean = walkUps.isBusy(woodcutter.isBusy())

    override fun finishing(): Boolean = false

    override fun interrupt() {
        walkUps.stop()
        woodcutter.interrupt()
    }

    override fun said(): Said? = walkUps.said()

    override fun act(left: Int?): StepOutcome {
        val view = woodcutter.look()
        val decision = retry.decide { skipped -> WoodcuttingPlanner.decide(view, skipped) }
        if (decision !is WalkTo) walkUps.stop()
        return carryOut(decision)
    }

    private fun carryOut(decision: WoodcuttingDecision): StepOutcome =
        when (decision) {
            is Chop -> chop(decision.tree)
            is WalkTo -> walkUps.walkTo(decision.tree)
            WalkToLocation -> StepOutcome.Away(woodcutter.anchor())
            is Settled -> decision.outcome
        }

    private fun chop(tree: Candidate): StepOutcome {
        woodcutter.chop(tree)
        return StepOutcome.Worked
    }
}
