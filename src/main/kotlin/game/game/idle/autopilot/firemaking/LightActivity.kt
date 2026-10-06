package game.idle.autopilot.firemaking

import game.idle.autopilot.firemaking.LightDecision.Blocked
import game.idle.autopilot.firemaking.LightDecision.Done
import game.idle.autopilot.firemaking.LightDecision.Light
import game.idle.autopilot.firemaking.LightDecision.StepAside
import game.idle.flow.StepActivity

/** What the light step can see and do for one player. [LunaLighter] is the in-game one. */
interface Lighter {

    fun isBusy(): Boolean

    fun look(): LightView

    fun light(slot: Int)

    /** Steps onto a free tile next to the player; false when there is none. */
    fun stepAside(): Boolean

    fun tell(message: String)
}

/**
 * What the light step knows when it decides: [logs] of the step's kinds carried, [lightable] the slot of one the
 * player has the level to light, [tileFree] whether a fire can be lit where the player stands.
 */
data class LightView(val hasTinderbox: Boolean, val logs: Int, val lightable: Int?, val tileFree: Boolean)

sealed interface LightDecision {

    data class Light(val slot: Int) : LightDecision

    data object StepAside : LightDecision

    data object Done : LightDecision

    data class Blocked(val reason: LightBlockedReason) : LightDecision
}

enum class LightBlockedReason(val message: String) {
    NO_TINDERBOX("Autopilot: you need a tinderbox to light the logs."),
    LEVEL_TOO_LOW("Autopilot: you need a higher Firemaking level to light these logs."),
    NO_ROOM("Autopilot: there is no free tile to light a fire on here."),
    NO_LOGS("Autopilot: there are no logs to light. Put a chop step before the light step."),
}

/**
 * Light a log where the player stands, stepping aside first when something takes the tile; done once the logs run
 * out after lighting some. With no logs from the start the step waits instead, so a flow never spins through empty
 * steps.
 */
object LightPlanner {

    fun decide(view: LightView, litSome: Boolean): LightDecision =
        when {
            !view.hasTinderbox -> Blocked(LightBlockedReason.NO_TINDERBOX)
            view.logs == 0 -> if (litSome) Done else Blocked(LightBlockedReason.NO_LOGS)
            view.lightable == null -> Blocked(LightBlockedReason.LEVEL_TOO_LOW)
            !view.tileFree -> StepAside
            else -> Light(view.lightable)
        }
}

/**
 * The light step: lights the step's logs one at a time until [amount] logs were used or, without an amount, until
 * none is left. A log counts as used once it leaves the inventory, which happens as the tinderbox strikes; the step
 * waits for that fire to catch before it ends, so a log is never left unlit on the ground.
 */
class LightActivity(private val lighter: Lighter, private val amount: Int? = null) : StepActivity {

    private var logsAtStart: Int? = null
    private var lastDecision: LightDecision? = null
    private var done = false

    override fun isBusy(): Boolean = lighter.isBusy()

    override fun isDone(): Boolean = done

    override fun act() {
        val view = lighter.look()
        val start = logsAtStart ?: view.logs.also { logsAtStart = it }
        if (amount != null && start - view.logs >= amount) {
            done = true
            return
        }
        lastDecision = carryOut(LightPlanner.decide(view, litSome = view.logs < start))
    }

    /** What was done: a step aside with nowhere to go blocks instead. */
    private fun carryOut(decision: LightDecision): LightDecision = when (decision) {
        is Light -> decision.also { lighter.light(it.slot) }
        StepAside -> if (lighter.stepAside()) decision else tellOnce(Blocked(LightBlockedReason.NO_ROOM))
        Done -> decision.also { done = true }
        is Blocked -> tellOnce(decision)
    }

    private fun tellOnce(decision: Blocked): Blocked {
        if (decision != lastDecision) lighter.tell(decision.reason.message)
        return decision
    }
}
