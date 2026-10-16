package game.idle.flow

import engine.combat.prayer.CombatPrayer

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

    /** Whether [prayer] is on for the player, whoever lit it. */
    fun praying(prayer: CombatPrayer): Boolean

    /** Whether the player could light [prayer] now: the base Prayer level is enough, points are left, prayers are not disabled. */
    fun canPray(prayer: CombatPrayer): Boolean

    /** Lights [prayer] as the tab's button does; true when it is on afterwards. */
    fun pray(prayer: CombatPrayer): Boolean

    /** Puts out [prayer]; true when it was on. */
    fun stopPraying(prayer: CombatPrayer): Boolean

    /** The special attack bar's energy, 0 to 100. */
    fun specialEnergy(): Int

    /**
     * The worn weapon's special attack, null without a worn weapon whose special Luna's scripts register (the bar of
     * any other lights nothing). A fact about the weapon alone, so a When on it holds whatever the player is doing.
     */
    fun special(): WornSpecial?

    /**
     * Whether the bar could be lit now: not lit already (the next hit takes it, or the boost is about to fire), not
     * locked by a special under way, and the player not locked (a teleport, death), when no click lands.
     */
    fun specialBarFree(): Boolean

    /** Whether the player fights something now. */
    fun fighting(): Boolean

    /** Lights the special attack bar as its button does; true when it is lit afterwards. */
    fun useSpecial(): Boolean

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
 * Praying lights what is off and can be lit, one act for the whole list, and the flow's [hold] keeps it on across
 * steps while a reflex whose When holds wants it; each decision first puts out what no reflex wants any more, with
 * the step going on (S13 part 3, Maxime 2026-10-16). Held prayers stay on while the player runs clear. A special
 * attack lights the bar once, with energy for one and only in a fight for a special that lands with the next hit
 * (S13 part 4, Maxime 2026-10-16); the lit bar is Luna's to use and keeps the reflex from lighting it again.
 */
class ReflexGuard(
    private val step: StepActivity,
    private val reflexes: List<ResolvedReflex>,
    private val body: ReflexBody,
    private val hold: PrayerHold,
) : StepActivity {

    private var leaving: Firing.Leave? = null
    private var outcome: ReflexOutcome? = null
    private val changes = mutableListOf<PrayerChange>()
    private val used = mutableListOf<Int>()

    override fun isBusy(): Boolean = step.isBusy() && leaving == null && firing() == null && !hold.pending(body, ::wanted)

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

    /** The prayers reflexes lit or put out since last asked, in order, for the chat lines. */
    fun prayed(): List<PrayerChange> = changes.toList().also { changes.clear() }

    /** The reflexes that lit the special attack bar since last asked, by number, for the chat lines. */
    fun used(): List<Int> = used.toList().also { used.clear() }

    override fun act() {
        val leave = leaving
        if (leave != null) {
            leave(leave)
            return
        }
        changes += hold.release(body, ::wanted)
        val firing = firing()
        // A release alone was the decision: the step's own action goes on, not clicked again.
        if (firing == null && step.isBusy()) return
        carryOut(firing)
    }

    private fun carryOut(firing: Firing?) = when (firing) {
        null -> step.act()
        is Firing.Eat -> body.eat(firing.slot)
        is Firing.Drink -> body.drink(firing.slot)
        is Firing.Pray -> pray(firing)
        is Firing.Special -> special(firing)
        is Firing.Leave -> leave(firing)
    }

    /** Luna has the last word: a bar it leaves off (locked, the energy gone since) is not said. */
    private fun special(firing: Firing.Special) {
        if (body.useSpecial()) used += firing.number
    }

    /** Lights each prayer in turn, as a 2006 player clicked them one after another; Luna has the last word on each. */
    private fun pray(firing: Firing.Pray) {
        for (prayer in firing.prayers) {
            if (!body.pray(prayer)) continue
            hold.lit(prayer, firing.number)
            changes += PrayerChange(firing.number, prayer, on = true)
        }
    }

    /** The prayers the pray reflexes whose When holds keep on. */
    private fun wanted(): Set<CombatPrayer> =
        reflexes.mapNotNull { reflex -> (reflex.action as? ReflexAction.Pray)?.takeIf { reflex.trigger.fired(body) != null } }.flatMap { it.prayers }.toSet()

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
            is ReflexAction.Pray -> action.prayers.filter(::prayable).takeIf { it.isNotEmpty() }?.let { Firing.Pray(reflex.number, it) }
            ReflexAction.SpecialAttack -> Firing.Special(reflex.number).takeIf { canSpecial() }
            ReflexAction.StopFlow -> Firing.Leave(reflex.number, fired, jumpTo = null)
            is ReflexAction.JumpTo -> Firing.Leave(reflex.number, fired, action.index)
        }

    /**
     * A prayer that is off and can be lit without putting out another: one of its group already on, by the player or
     * another reflex, is left alone, so no reflex swaps prayers back and forth (Maxime, 2026-10-16).
     */
    private fun prayable(prayer: CombatPrayer): Boolean = !body.praying(prayer) && body.canPray(prayer) && groupFree(prayer)

    private fun groupFree(prayer: CombatPrayer): Boolean = prayer.group?.getPrayers()?.none(body::praying) ?: true

    /**
     * The bar is free and holds energy for one; a special that lands with the next hit waits for a fight, so the bar
     * is never lit outside one and left waiting (Maxime, 2026-10-16), while a boost fires by itself. A bar Luna would
     * not light is never asked for: asking in vain would hold the step and every reflex after this one.
     */
    private fun canSpecial(): Boolean {
        val worn = body.special() ?: return false
        return body.specialBarFree() && body.specialEnergy() >= worn.drain && (!worn.attack || body.fighting())
    }

    private sealed interface Firing {

        data class Eat(val slot: Int) : Firing

        data class Drink(val slot: Int) : Firing

        /** Reflex [number] lights [prayers], the ones of its list that can be lit. */
        data class Pray(val number: Int, val prayers: List<CombatPrayer>) : Firing

        /** Reflex [number] lights the special attack bar. */
        data class Special(val number: Int) : Firing

        /** A stop, or a jump to the step at [jumpTo]. */
        data class Leave(val number: Int, val fired: Fired, val jumpTo: Int?) : Firing
    }
}
