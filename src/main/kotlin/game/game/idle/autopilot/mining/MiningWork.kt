package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.Settled
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.location.Tile

/** What the mine step can see and do for one player. [LunaMiner] is the in-game one. */
interface Miner {

    fun isBusy(): Boolean

    fun look(): MiningView

    fun mine(rock: Candidate)

    fun walkTo(rock: Candidate)

    /** The anchor of the step's area, where a walk back goes. */
    fun anchor(): Tile

    /** How many of the step's ores the player carries. */
    fun ores(): Int

    /** Breaks off the mining in progress. */
    fun interrupt()
}

/**
 * The mine step's work: carries out [MiningPlanner] decisions, its ores the count. Luna's mining ends with each ore, so
 * the step clicks again on its next decision.
 */
class MiningWork(private val miner: Miner) : StepWork {

    private val retry = RetrySkip.forTheTurn(miner::ores)

    override val meter: Meter = Meter.Rises(miner::ores)

    override fun isBusy(): Boolean = miner.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = miner.interrupt()

    override fun act(left: Int?): StepOutcome {
        val view = miner.look()
        return carryOut(retry.decide { skipped -> MiningPlanner.decide(view, skipped) })
    }

    private fun carryOut(decision: MiningDecision): StepOutcome =
        when (decision) {
            is Mine -> mine(decision.rock)
            is WalkTo -> walkTo(decision.rock)
            WalkToLocation -> StepOutcome.Away(miner.anchor())
            is Settled -> decision.outcome
        }

    private fun mine(rock: Candidate): StepOutcome {
        miner.mine(rock)
        return StepOutcome.Worked
    }

    private fun walkTo(rock: Candidate): StepOutcome {
        miner.walkTo(rock)
        return StepOutcome.Approached
    }
}
