package game.idle.autopilot.mining

import game.idle.autopilot.mining.MiningDecision.Blocked
import game.idle.autopilot.mining.MiningDecision.Mine
import game.idle.autopilot.mining.MiningDecision.OnRock
import game.idle.autopilot.mining.MiningDecision.WalkTo
import game.idle.autopilot.mining.MiningDecision.WalkToLocation
import game.idle.flow.StepActivity
import game.skill.mining.Ore
import io.luna.game.model.Position

/** What the mine step can see and do for one player. [LunaMiner] is the in-game one. */
interface Miner {

    fun isBusy(): Boolean

    fun look(): MiningView

    fun mine(rock: RockCandidate)

    fun walkTo(rock: RockCandidate)

    fun walkToLocation()

    /** How many of the step's ores the player carries. */
    fun ores(): Int

    /** Stops the mining in progress. */
    fun stop()
}

/**
 * Carries out [MiningPlanner] decisions for one [ore]. Luna's mining ends with each ore, so the step clicks again
 * until [amount] ores were mined or, without an amount, until the inventory fills up after mining some; an inventory
 * already full at the start blocks instead. Mining that reaches the amount is stopped at once. A rock that gets the
 * same decision twice in a row with no ore mined in between (walked to but still out of reach, or clicked without
 * mining starting) is skipped for as long as the step runs, so an unreachable rock cannot trap the player.
 */
class MiningActivity(private val miner: Miner, private val ore: Ore, private val amount: Int? = null) : StepActivity {

    private val skippedRocks = mutableSetOf<Position>()
    private var lastDecision: MiningDecision? = null
    private var oresAtLastDecision = 0
    private var oresAtStart: Int? = null
    private var mined = false
    private var done = false

    override fun isBusy(): Boolean = miner.isBusy() && !amountReached()

    override fun isDone(): Boolean = done

    override fun blocked(): String? = (lastDecision as? Blocked)?.reason?.message

    override fun act() {
        if (oresAtStart == null) oresAtStart = miner.ores()
        if (amountReached()) {
            miner.stop()
            done = true
            return
        }
        val view = miner.look()
        if (mined && view.inventoryFull) {
            done = true
            return
        }
        val decision = decideSkippingRetries(view)
        carryOut(decision)
        lastDecision = decision
        oresAtLastDecision = miner.ores()
    }

    private fun amountReached(): Boolean {
        val start = oresAtStart ?: return false
        return amount != null && miner.ores() - start >= amount
    }

    private fun carryOut(decision: MiningDecision) = when (decision) {
        // A block does nothing here (the autopilot tells it); as the last arm its empty body would leave JaCoCo a branch
        // no test can reach, so it comes first.
        is Blocked -> Unit
        is Mine -> mine(decision.rock)
        is WalkTo -> miner.walkTo(decision.rock)
        WalkToLocation -> miner.walkToLocation()
    }

    private fun mine(rock: RockCandidate) {
        mined = true
        miner.mine(rock)
    }

    private fun decideSkippingRetries(view: MiningView): MiningDecision {
        val decision = decide(view)
        val retried = retriedRock(decision) ?: return decision
        skippedRocks += retried.position
        return decide(view)
    }

    private fun decide(view: MiningView): MiningDecision =
        MiningPlanner.decide(view.copy(rocks = view.rocks.filterNot { it.position in skippedRocks }), ore)

    /** The rock [decision] aims at when the previous decision was the same step on it and no ore came of it. */
    private fun retriedRock(decision: MiningDecision): RockCandidate? {
        val attempt = decision as? OnRock ?: return null
        val last = lastDecision as? OnRock ?: return null
        val nothingMined = miner.ores() == oresAtLastDecision
        return attempt.rock.takeIf { nothingMined && attempt::class == last::class && it.position == last.rock.position }
    }
}
