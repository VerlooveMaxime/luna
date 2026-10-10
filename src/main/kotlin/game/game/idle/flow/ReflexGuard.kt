package game.idle.flow

/** What reflexes see and do for one player (S07c). `LunaReflexBody` is the in-game one. */
interface ReflexBody {

    fun health(): Health

    /** The bag slot of the first of [foods] the bag holds, in bag order, or of any food when [foods] is empty; null for none. */
    fun foodSlot(foods: Set<Int>): Int?

    fun eat(slot: Int)

    /** Whether anything that can get at the player attacks them. */
    fun underAttack(): Boolean

    /** Runs from what attacks the player; false when there is nowhere to run. */
    fun flee(): Boolean

    fun dead(): Boolean
}

/** A step activity whose Amount counts something: how much of it the step has done in its turn (S07c's progress). */
interface CountsAmount {

    fun amountDone(): Int
}

/** What a reflex ends with, besides going on with the step: the flow stops, or goes on from another step. */
sealed interface ReflexOutcome {

    val message: String

    data class Stop(override val message: String) : ReflexOutcome

    /** The flow goes on from the step at [index]; [message] tells the player why. */
    data class Jump(val index: Int, override val message: String) : ReflexOutcome
}

/**
 * The running [step]'s activity with the [reflexes] attached to it, in its order (S07c). At each decision the first
 * reflex whose When holds and whose Do can be done fires, else the step acts as usual; the step is not busy while one
 * would fire, so the driver decides after its pause, mid-fight included. Eating leaves the step going, as a 2006 player's
 * meal did. Running away owns the step until nothing attacks the player or there is nowhere to run, then its Then: the
 * flow stops or jumps (Maxime, 2026-10-10).
 */
class ReflexGuard(private val step: StepActivity, private val reflexes: List<ResolvedReflex>, private val body: ReflexBody) : StepActivity {

    private var running: Running? = null
    private var outcome: ReflexOutcome? = null

    override fun isBusy(): Boolean = step.isBusy() && running == null && firing() == null

    override fun isDone(): Boolean = step.isDone()

    override fun blocked(): String? = step.blocked()

    override fun stopReason(): String? = (outcome as? ReflexOutcome.Stop)?.message ?: step.stopReason()

    /** Where a reflex sends the flow once its run is over; null while the step goes on. */
    fun jump(): ReflexOutcome.Jump? = outcome as? ReflexOutcome.Jump

    /** How much the step has done in its turn, 0 for a step that counts nothing (walk, bank, drop). */
    fun progress(): Int = (step as? CountsAmount)?.amountDone() ?: 0

    override fun act() {
        carryOut(running?.run ?: firing())
    }

    private fun carryOut(firing: Firing?) = when (firing) {
        null -> step.act()
        is Firing.Eat -> body.eat(firing.slot)
        is Firing.RunAway -> runAway(firing)
    }

    /** The Then's message gives the hitpoints the reflex fired at, not those the run ended with. */
    private fun runAway(run: Firing.RunAway) {
        val started = running ?: Running(run, body.health())
        running = started
        if (body.underAttack() && body.flee()) return
        running = null
        outcome = then(run, started.health)
    }

    private fun then(run: Firing.RunAway, health: Health): ReflexOutcome =
        when (val then = run.then) {
            ReflexThen.StopFlow -> ReflexOutcome.Stop("Autopilot: stopped by reflex ${run.number} at ${health.text()} hitpoints.")
            is ReflexThen.JumpTo -> ReflexOutcome.Jump(
                then.index,
                "Autopilot: reflex ${run.number} ran at ${health.text()} hitpoints, jumping to step ${then.index + 1}.",
            )
        }

    /** The first reflex whose When holds and whose Do can be done, as what it would do. */
    private fun firing(): Firing? = reflexes.firstNotNullOfOrNull { reflex -> if (reflex.trigger.holds(body)) firing(reflex) else null }

    private fun firing(reflex: ResolvedReflex): Firing? =
        when (val action = reflex.action) {
            is ReflexAction.Eat -> body.foodSlot(action.foods)?.let(Firing::Eat)
            is ReflexAction.RunAway -> Firing.RunAway(reflex.number, action.then)
        }

    private data class Running(val run: Firing.RunAway, val health: Health)

    private sealed interface Firing {

        data class Eat(val slot: Int) : Firing

        data class RunAway(val number: Int, val then: ReflexThen) : Firing
    }
}
