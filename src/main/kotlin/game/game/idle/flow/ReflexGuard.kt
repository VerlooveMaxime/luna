package game.idle.flow

/** What reflexes see and do for one player (S07c). `LunaReflexBody` is the in-game one. */
interface ReflexBody {

    fun health(): Health

    fun level(skill: Int): Int

    /** The bag slot of the first held id in [doses], or null when none is held. */
    fun potionSlot(doses: List<Int>): Int?

    fun drink(slot: Int)

    /** The bag slot of the first of [foods] the bag holds, in bag order, or of any food when [foods] is empty; null for none. */
    fun foodSlot(foods: Set<Int>): Int?

    fun eat(slot: Int)

    /** Whether anything that can get at the player attacks them. */
    fun underAttack(): Boolean

    /**
     * Runs from what attacks the player, running while run energy lasts (Maxime, 2026-10-10); stays put when no tile
     * gets farther from them.
     */
    fun flee()

    /** The flight is over: running goes back to the player's own run toggle. */
    fun calm()

    fun dead(): Boolean
}

/**
 * What a reflex's stop or jump ends with, besides going on with the step: the flow stops, or goes on from the step at
 * [Jump.index]. Both say which reflex ([number]) fired and at what ([fired]), for the line the player is told.
 */
sealed interface ReflexOutcome {

    val number: Int

    val fired: Fired

    data class Stop(override val number: Int, override val fired: Fired) : ReflexOutcome

    data class Jump(val index: Int, override val number: Int, override val fired: Fired) : ReflexOutcome
}

/**
 * The running [step]'s activity with the [reflexes] attached to it, in its order (S07c). At each decision the first
 * reflex whose When holds and whose Do can be done fires, else the step acts as usual; the step is not busy while one
 * would fire, so the driver decides after its pause, mid-fight included. Eating and drinking leave the step going, as a 2006 player's
 * consumables did. A stop or a jump owns the step until nothing attacks the player, who runs clear meanwhile, cornered or not,
 * and only then stops the flow or jumps (Maxime, 2026-10-10: never while attacked; S13, Q15: every stop and jump).
 */
class ReflexGuard(private val step: StepActivity, private val reflexes: List<ResolvedReflex>, private val body: ReflexBody) : StepActivity {

    private var leaving: Firing.Leave? = null
    private var outcome: ReflexOutcome? = null

    override fun isBusy(): Boolean = step.isBusy() && leaving == null && firing() == null

    override fun isDone(): Boolean = step.isDone()

    /** None while the player runs clear before a stop or a jump: the step no longer works, so its block is stale. */
    override fun blocked(): Reason? = if (leaving != null) null else step.blocked()

    override fun stopping(): StepStop? = (outcome as? ReflexOutcome.Stop)?.let { StepStop.Reflex(it.number, it.fired) } ?: step.stopping()

    override fun said(): Said? = step.said()

    /** Running clear before a stop or a jump is what the step waits for, as the slot and the overlay show it. */
    override fun waiting(): Reason? = leaving?.let { Reason.RunningClear(it.jumpTo?.plus(1)) } ?: step.waiting()

    override fun amountDone(): Int = step.amountDone()

    override fun worked(): Boolean = step.worked()

    override fun endTurn() = step.endTurn()

    /** Where a reflex sends the flow once the player is clear; null while the step goes on. */
    fun jump(): ReflexOutcome.Jump? = outcome as? ReflexOutcome.Jump

    override fun act() {
        carryOut(leaving ?: firing())
    }

    private fun carryOut(firing: Firing?) = when (firing) {
        null -> step.act()
        is Firing.Eat -> body.eat(firing.slot)
        is Firing.Drink -> body.drink(firing.slot)
        is Firing.Leave -> leave(firing)
    }

    /** The outcome gives what the reflex fired at, not what the player was at once clear. */
    private fun leave(firing: Firing.Leave) {
        if (body.underAttack()) {
            leaving = firing
            body.flee()
            return
        }
        leaving = null
        body.calm()
        outcome = firing.jumpTo?.let { ReflexOutcome.Jump(it, firing.number, firing.fired) } ?: ReflexOutcome.Stop(firing.number, firing.fired)
    }

    /** The first reflex whose When holds and whose Do can be done, as what it would do. */
    private fun firing(): Firing? = reflexes.firstNotNullOfOrNull { reflex -> reflex.trigger.fired(body)?.let { firing(reflex, it) } }

    private fun firing(reflex: ResolvedReflex, fired: Fired): Firing? =
        when (val action = reflex.action) {
            is ReflexAction.Eat -> body.foodSlot(action.foods)?.let(Firing::Eat)
            is ReflexAction.Drink -> body.potionSlot(action.doses)?.let(Firing::Drink)
            ReflexAction.StopFlow -> Firing.Leave(reflex.number, fired, jumpTo = null)
            is ReflexAction.JumpTo -> Firing.Leave(reflex.number, fired, action.index)
        }

    private sealed interface Firing {

        data class Eat(val slot: Int) : Firing

        data class Drink(val slot: Int) : Firing

        /** A stop, or a jump to the step at [jumpTo]. */
        data class Leave(val number: Int, val fired: Fired, val jumpTo: Int?) : Firing
    }
}
