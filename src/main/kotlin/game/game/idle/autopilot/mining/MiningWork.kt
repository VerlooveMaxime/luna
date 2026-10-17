package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.Settled
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.idle.autopilot.travel.WalkUps
import game.idle.flow.Meter
import game.idle.flow.RetrySkip
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.location.Tile

/** What the mine step can see and do for one player. [LunaMiner] is the in-game one. */
interface Miner {

    fun isBusy(): Boolean

    fun look(): MiningView

    fun mine(rock: Candidate)

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
class MiningWork(private val miner: Miner, private val walkUps: WalkUps) : StepWork {

    private val retry = RetrySkip.forTheTurn(miner::ores, walkUps::setAside)

    override val meter: Meter = Meter.Rises(miner::ores)

    override fun isBusy(): Boolean = walkUps.isBusy(miner.isBusy())

    override fun finishing(): Boolean = false

    override fun interrupt() {
        walkUps.stop()
        miner.interrupt()
    }

    override fun said(): Said? = walkUps.said()

    override fun act(left: Int?): StepOutcome {
        val view = miner.look()
        val decision = retry.decide { skipped -> MiningPlanner.decide(view, skipped) }
        if (decision !is WalkTo) walkUps.stop()
        return carryOut(decision)
    }

    private fun carryOut(decision: MiningDecision): StepOutcome =
        when (decision) {
            is Mine -> mine(decision.rock)
            is WalkTo -> walkUps.walkTo(decision.rock)
            WalkToLocation -> StepOutcome.Away(miner.anchor())
            is Settled -> decision.outcome
        }

    private fun mine(rock: Candidate): StepOutcome {
        miner.mine(rock)
        return StepOutcome.Worked
    }
}
