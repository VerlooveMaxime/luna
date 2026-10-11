package game.idle.autopilot.firemaking

import game.idle.autopilot.firemaking.LightDecision.Blocked
import game.idle.autopilot.firemaking.LightDecision.Done
import game.idle.autopilot.firemaking.LightDecision.Light
import game.idle.autopilot.firemaking.LightDecision.MoveToFreeTile
import game.idle.flow.CountsAmount
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepActivity
import game.idle.flow.StepStop
import game.idle.flow.Supply
import game.idle.flow.Target
import game.idle.flow.ToolNeed
import game.skill.firemaking.Firemaking
import io.luna.game.model.mob.Skill

/** What the light step can see and do for one player. [LunaLighter] is the in-game one. */
interface Lighter {

    fun isBusy(): Boolean

    fun look(): LightView

    fun light(slot: Int)

    /** Walks to a free tile near where the step started; false when there is none close enough. */
    fun moveToFreeTile(): Boolean
}

/**
 * What the light step knows when it decides: [logs] of the step's kinds carried, [lightable] the slot of one the
 * player has the level to light, [level] the Firemaking level the easiest of those logs needs (1 with none carried),
 * [tileFree] whether a fire can be lit where the player stands.
 */
data class LightView(val hasTinderbox: Boolean, val logs: Int, val lightable: Int?, val level: Int, val tileFree: Boolean)

sealed interface LightDecision {

    data class Light(val slot: Int) : LightDecision

    data object MoveToFreeTile : LightDecision

    data object Done : LightDecision

    data class Blocked(val reason: Reason) : LightDecision
}

/**
 * Light a log where the player stands, moving to a free tile first when something takes the tile; done once the logs
 * run out after lighting some. With no logs from the start the step waits instead, so a flow never spins through empty
 * steps.
 */
object LightPlanner {

    /** The tinderbox; what the step needs and blocks on. */
    val TINDERBOX = ToolNeed(word = null, mapOf(Firemaking.TINDERBOX to 1))

    fun decide(view: LightView, litSome: Boolean): LightDecision =
        when {
            !view.hasTinderbox -> Blocked(Reason.NeedsTool(TINDERBOX))
            view.logs == 0 -> if (litSome) Done else Blocked(Reason.NothingToUse(Supply.LOGS))
            view.lightable == null -> Blocked(Reason.NeedsLevel(Skill.FIREMAKING, view.level))
            !view.tileFree -> MoveToFreeTile
            else -> Light(view.lightable)
        }
}

/**
 * The light step: lights the step's logs one at a time until [amount] logs were used or, without an amount, until
 * none is left. A log counts as used once it leaves the inventory, which happens as the tinderbox strikes; the step
 * waits for that fire to catch before it ends, so a log is never left unlit on the ground.
 */
class LightActivity(private val lighter: Lighter, private val amount: Int? = null) : StepActivity, CountsAmount {

    private var logsAtStart: Int? = null
    private var lastDecision: LightDecision? = null
    private var done = false

    override fun isBusy(): Boolean = lighter.isBusy()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = (lastDecision as? Blocked)?.reason

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

    override fun amountDone(): Int = logsAtStart?.let { it - lighter.look().logs } ?: 0

    override fun act() {
        val view = lighter.look()
        val start = logsAtStart ?: view.logs.also { logsAtStart = it }
        if (amount != null && start - view.logs >= amount) {
            done = true
            return
        }
        lastDecision = carryOut(LightPlanner.decide(view, litSome = view.logs < start))
    }

    /** What was done: a move with no free tile close enough blocks instead. */
    private fun carryOut(decision: LightDecision): LightDecision = when (decision) {
        is Light -> decision.also { lighter.light(it.slot) }
        MoveToFreeTile -> if (lighter.moveToFreeTile()) decision else Blocked(Reason.NoneInReach(Target.FREE_TILE))
        Done -> decision.also { done = true }
        is Blocked -> decision
    }
}
