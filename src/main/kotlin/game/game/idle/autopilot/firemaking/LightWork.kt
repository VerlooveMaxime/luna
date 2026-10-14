package game.idle.autopilot.firemaking

import game.idle.autopilot.firemaking.LightDecision.Light
import game.idle.autopilot.firemaking.LightDecision.MoveToFreeTile
import game.idle.autopilot.firemaking.LightDecision.Settled
import game.idle.flow.Meter
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.Target

/** What the light step can see and do for one player. [LunaLighter] is the in-game one. */
interface Lighter {

    fun isBusy(): Boolean

    fun look(): LightView

    fun light(slot: Int)

    /** Walks to a free tile near where the step started; false when there is none close enough. */
    fun moveToFreeTile(): Boolean

    /** How many logs of the step's kinds the player carries. */
    fun logs(): Int

    /** Breaks off what the lighter has the player doing; the step asks only once nothing is ([LightWork.finishing]). */
    fun interrupt()
}

/**
 * What the light step knows when it decides: [lightable] the slot of one of the step's logs the player has the level
 * to light, null with none, and [tileFree] whether a fire can be lit where the player stands.
 */
data class LightView(val lightable: Int?, val tileFree: Boolean)

sealed interface LightDecision {

    data class Light(val slot: Int) : LightDecision

    data object MoveToFreeTile : LightDecision

    /** A decision that is one of the outcomes every kind shares. */
    data class Settled(val outcome: StepOutcome) : LightDecision
}

/**
 * Light a log where the player stands, moving to a free tile first when something takes the tile; with no log left to
 * light the step has nothing to work with. The tinderbox and the level are the shared turn's to check
 * ([game.idle.flow.StepTurn]).
 */
object LightPlanner {

    fun decide(view: LightView): LightDecision =
        when {
            view.lightable == null -> Settled(StepOutcome.Empty)
            !view.tileFree -> MoveToFreeTile
            else -> Light(view.lightable)
        }
}

/**
 * The light step's work: carries out [LightPlanner] decisions, the logs used the count. A log is used once it leaves
 * the bag, which happens as the tinderbox strikes, so the step waits for that fire to catch before it ends and a log is
 * never left unlit on the ground: a fire being lit is never broken off, the step staying busy until it catches
 * ([finishing]).
 */
class LightWork(private val lighter: Lighter) : StepWork {

    override val meter: Meter = Meter.Falls(lighter::logs)

    override fun isBusy(): Boolean = lighter.isBusy()

    override fun finishing(): Boolean = lighter.isBusy()

    override fun interrupt() = lighter.interrupt()

    override fun act(left: Int?): StepOutcome = carryOut(LightPlanner.decide(lighter.look()))

    private fun carryOut(decision: LightDecision): StepOutcome =
        when (decision) {
            is Light -> light(decision.slot)
            MoveToFreeTile -> moveToFreeTile()
            is Settled -> decision.outcome
        }

    private fun light(slot: Int): StepOutcome {
        lighter.light(slot)
        return StepOutcome.Worked
    }

    /** With no free tile close enough the player must make room, so the step blocks. */
    private fun moveToFreeTile(): StepOutcome =
        if (lighter.moveToFreeTile()) StepOutcome.Approached else StepOutcome.Blocked(Reason.NoneInReach(Target.FREE_TILE))
}
