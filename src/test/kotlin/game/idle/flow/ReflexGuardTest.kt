package game.idle.flow

import engine.combat.prayer.CombatPrayer
import engine.combat.prayer.CombatPrayer.BURST_OF_STRENGTH
import engine.combat.prayer.CombatPrayer.PROTECT_FROM_MELEE
import engine.combat.prayer.CombatPrayer.RAPID_HEAL
import engine.combat.prayer.CombatPrayer.ROCK_SKIN
import engine.combat.prayer.CombatPrayer.THICK_SKIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReflexGuardTest {

    private val log = mutableListOf<String>()
    private val step = FakeStepActivity("fight", log)
    private val body = FakeReflexBody()
    private val trout = 333
    private val lobster = 379

    private fun below(percent: Int) = ReflexTrigger.HitpointsBelow(percent)

    private fun eat(number: Int = 1, foods: Set<Int> = emptySet(), percent: Int = 50) = ResolvedReflex(number, below(percent), ReflexAction.Eat(foods))

    private fun stop(number: Int = 2, trigger: ReflexTrigger = below(50)) = ResolvedReflex(number, trigger, ReflexAction.StopFlow)

    private fun jump(index: Int, number: Int = 2) = ResolvedReflex(number, below(50), ReflexAction.JumpTo(index))

    private fun guard(vararg reflexes: ResolvedReflex) = ReflexGuard(step, reflexes.toList(), body, hold)

    private val hold = PrayerHold()

    private fun pray(vararg prayers: CombatPrayer, number: Int = 1, trigger: ReflexTrigger = below(50)) = ResolvedReflex(number, trigger, ReflexAction.Pray(prayers.toList()))

    private val hurtTo = Health(9, 20)

    private val atHurt = Fired.AtHitpoints(hurtTo)

    private fun hurt() {
        body.health = hurtTo
    }

    @Test
    fun `with no reflex firing the step acts`() {
        val guard = guard(eat())
        body.bag[0] = trout

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `an eat reflex eats the first food in the bag`() {
        val guard = guard(eat())
        body.bag[3] = trout
        body.bag[1] = lobster
        hurt()

        guard.act()

        assertEquals(listOf("eat 1"), body.done)
    }

    @Test
    fun `while a reflex fires the step does not act`() {
        val guard = guard(eat())
        body.bag[0] = trout
        hurt()

        guard.act()

        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `an eat reflex eats only its foods`() {
        val guard = guard(eat(foods = setOf(trout)))
        body.bag[1] = lobster
        body.bag[3] = trout
        hurt()

        guard.act()

        assertEquals(listOf("eat 3"), body.done)
    }

    @Test
    fun `an eat reflex with none of its foods left lets the step act`() {
        val guard = guard(eat(foods = setOf(trout)))
        body.bag[1] = lobster
        hurt()

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `an eat reflex with no food left falls through to the next`() {
        val guard = guard(eat(), stop())
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
    }

    @Test
    fun `the first reflex in the step's order fires`() {
        val guard = guard(stop(number = 1), eat(number = 2))
        body.bag[0] = trout
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(1, atHurt), guard.stopping())
    }

    @Test
    fun `a reflex whose hitpoints have not fallen that far does not fire`() {
        val guard = guard(eat(number = 1, percent = 25), eat(number = 2, foods = setOf(trout)))
        body.bag[0] = lobster
        body.bag[1] = trout
        hurt()

        guard.act()

        assertEquals(listOf("eat 1"), body.done)
    }

    @Test
    fun `an under attack reflex fires while something attacks the player`() {
        val guard = guard(stop(trigger = ReflexTrigger.UnderAttack))
        body.attacked = true
        guard.act()
        body.attacked = false

        guard.act()

        assertEquals(StepStop.Reflex(2, Fired.UnderAttack), guard.stopping())
    }

    @Test
    fun `an under attack reflex does not fire while nothing attacks`() {
        val guard = guard(stop(trigger = ReflexTrigger.UnderAttack))

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `a stop while attacked runs clear first`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()

        guard.act()

        assertEquals(listOf("flee"), body.done)
        assertNull(guard.stopping())
    }

    @Test
    fun `running clear goes on while attacked, healed or not`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(20, 20)

        guard.act()

        assertEquals(listOf("flee", "flee"), body.done)
    }

    @Test
    fun `once nothing attacks a jump goes on from its step`() {
        val guard = guard(jump(index = 1))
        body.attacked = true
        hurt()
        guard.act()
        body.attacked = false

        guard.act()

        assertEquals(ReflexOutcome.Jump(index = 1, number = 2, fired = atHurt), guard.jump())
    }

    @Test
    fun `running clear before a stop is what the step waits for`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()

        guard.act()

        assertEquals(Reason.RunningClear(), guard.waiting())
    }

    @Test
    fun `running clear hides the step's block, which no longer holds`() {
        val guard = guard(stop())
        step.blocked = Reason.NoneInReach(Target.NPC)
        body.attacked = true
        hurt()

        guard.act()

        assertNull(guard.blocked())
    }

    @Test
    fun `running clear before a jump names the step it goes on from`() {
        val guard = guard(jump(index = 2))
        body.attacked = true
        hurt()

        guard.act()

        assertEquals(Reason.RunningClear(toStep = 3), guard.waiting())
    }

    @Test
    fun `the stop gives the hitpoints the reflex fired at`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(12, 20)
        body.attacked = false

        guard.act()

        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
    }

    @Test
    fun `with nothing attacking a stop stops at once`() {
        val guard = guard(stop())
        hurt()

        guard.act()

        assertEquals(listOf("calm"), body.done)
        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
    }

    @Test
    fun `running clear puts the player's running back once clear`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()
        guard.act()
        body.attacked = false

        guard.act()

        assertEquals(listOf("flee", "calm"), body.done)
    }

    @Test
    fun `a stop sends nobody anywhere`() {
        val guard = guard(stop())
        hurt()

        guard.act()

        assertNull(guard.jump())
    }

    @Test
    fun `a step going on has no jump and its own reason to stop`() {
        val guard = guard()
        step.stop = StepStop.Stuck(Reason.BankFull)

        guard.act()

        assertNull(guard.jump())
        assertEquals(StepStop.Stuck(Reason.BankFull), guard.stopping())
    }

    @Test
    fun `what the step says is the guard's`() {
        step.said = Said.Skipped(Reason.BankFull)

        assertEquals(Said.Skipped(Reason.BankFull), guard().said())
    }

    @Test
    fun `a busy step with no reflex firing keeps the guard busy`() {
        step.busy = true

        assertTrue(guard(eat()).isBusy())
    }

    @Test
    fun `a step that is not busy lets the guard act`() {
        assertFalse(guard().isBusy())
    }

    @Test
    fun `a reflex that would fire lets the guard act mid-fight`() {
        step.busy = true
        body.bag[0] = trout
        hurt()

        assertFalse(guard(eat()).isBusy())
    }

    @Test
    fun `running clear lets the guard act at each decision`() {
        val guard = guard(stop())
        body.attacked = true
        hurt()
        guard.act()
        body.health = Health(20, 20)
        step.busy = true

        assertFalse(guard.isBusy())
    }

    @Test
    fun `the step's end and block are the guard's`() {
        step.done = true
        step.blocked = Reason.NoneInReach(Target.NPC)

        val guard = guard()

        assertEquals(true to Reason.NoneInReach(Target.NPC), guard.isDone() to guard.blocked())
    }

    @Test
    fun `the step's count is the guard's`() {
        step.amountDone = 3

        assertEquals(3, guard().amountDone())
    }

    @Test
    fun `the step's wait and whether it worked are the guard's`() {
        step.waiting = Reason.NoneInReach(Target.NPC)
        step.worked = false

        val guard = guard()

        assertEquals(Reason.NoneInReach(Target.NPC) to false, guard.waiting() to guard.worked())
    }

    @Test
    fun `ending the turn ends the step's`() {
        guard().endTurn()

        assertEquals(1, step.ended)
    }

    @Test
    fun `health below a share compares against full hitpoints`() {
        assertTrue(Health(7, 10).below(75))
        assertFalse(Health(5, 10).below(50))
    }

    @Test
    fun `a skill condition observes the current level and fires again after the boost wears off`() {
        val trigger = ReflexTrigger.SkillBelow(2, 25)
        body.levels[2] = 24
        val first = trigger.fired(body)
        body.levels[2] = 28
        val boosted = trigger.fired(body)
        body.levels[2] = 25
        val equal = trigger.fired(body)
        body.levels[2] = 24

        assertEquals(listOf(Fired.AtSkill(2, 24), null, null, Fired.AtSkill(2, 24)), listOf(first, boosted, equal, trigger.fired(body)))
    }

    @Test
    fun `drinking interrupts a busy step without ending it`() {
        val guard = guard(ResolvedReflex(1, below(50), ReflexAction.Drink(listOf(119, 117, 115, 113))))
        body.bag[3] = 113
        hurt()
        step.busy = true

        assertFalse(guard.isBusy())
        guard.act()

        assertEquals(listOf("drink 3"), body.done)
        assertEquals(emptyList<String>(), log)
        assertNull(guard.stopping())
    }

    @Test
    fun `an exhausted potion list lets the next reflex fire`() {
        val guard = guard(ResolvedReflex(1, below(50), ReflexAction.Drink(listOf(113))), stop())
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
    }

    @Test
    fun `an exhausted potion list leaves the step running when no other reflex fires`() {
        val guard = guard(ResolvedReflex(1, below(50), ReflexAction.Drink(listOf(113))))
        hurt()

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `a pray reflex lights the prayers of its list that are off, in one act`() {
        val guard = guard(pray(THICK_SKIN, BURST_OF_STRENGTH))
        hurt()

        guard.act()

        assertEquals(listOf("pray thick_skin", "pray burst_of_strength"), body.done)
        assertEquals(setOf(THICK_SKIN, BURST_OF_STRENGTH), body.lit)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `what a reflex lit is said once, by its number`() {
        val guard = guard(pray(THICK_SKIN, BURST_OF_STRENGTH, number = 2))
        hurt()

        guard.act()

        assertEquals(listOf(PrayerChange(2, THICK_SKIN, on = true), PrayerChange(2, BURST_OF_STRENGTH, on = true)), guard.prayed())
        assertEquals(emptyList<PrayerChange>(), guard.prayed())
    }

    @Test
    fun `a pray reflex lets the guard act while the step is busy`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        step.busy = true

        assertFalse(guard.isBusy())
    }

    @Test
    fun `a pray reflex whose prayers are all on lets the next reflex fire`() {
        val guard = guard(pray(THICK_SKIN), stop())
        body.lit += THICK_SKIN
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
        assertEquals(emptyList<String>(), body.done.filter { it.startsWith("pray") })
    }

    @Test
    fun `a pray reflex with nothing to light lets the step act`() {
        val guard = guard(pray(THICK_SKIN))
        body.lit += THICK_SKIN
        hurt()

        guard.act()

        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `a pray reflex with nothing to light keeps a busy guard busy`() {
        val guard = guard(pray(THICK_SKIN))
        body.lit += THICK_SKIN
        hurt()
        step.busy = true

        assertTrue(guard.isBusy())
    }

    @Test
    fun `a prayer whose group has one on, by anyone, is left alone`() {
        val guard = guard(pray(THICK_SKIN, BURST_OF_STRENGTH))
        body.lit += ROCK_SKIN
        hurt()

        guard.act()

        assertEquals(listOf("pray burst_of_strength"), body.done)
        assertEquals(setOf(ROCK_SKIN, BURST_OF_STRENGTH), body.lit)
    }

    @Test
    fun `a prayer of no group is lit beside any other`() {
        val guard = guard(pray(RAPID_HEAL))
        body.lit += THICK_SKIN
        hurt()

        guard.act()

        assertEquals(setOf(THICK_SKIN, RAPID_HEAL), body.lit)
    }

    @Test
    fun `without prayer points a pray reflex falls through to the next`() {
        val guard = guard(pray(THICK_SKIN), stop())
        body.prayerPoints = 0
        hurt()

        guard.act()

        assertEquals(StepStop.Reflex(2, atHurt), guard.stopping())
        assertEquals(listOf("calm"), body.done)
    }

    @Test
    fun `a prayer above the base level is not asked for`() {
        val guard = guard(pray(PROTECT_FROM_MELEE))
        body.prayerLevel = 10
        hurt()

        guard.act()

        assertEquals(emptyList<String>(), body.done)
        assertEquals(listOf("fight:1"), log)
        assertEquals(emptyList<PrayerChange>(), guard.prayed())
    }

    @Test
    fun `while prayers are disabled none is asked for`() {
        val guard = guard(pray(THICK_SKIN))
        body.prayersDisabled = true
        hurt()

        guard.act()

        assertEquals(emptyList<String>(), body.done)
        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `a prayer Luna turns down is neither held nor said`() {
        val guard = guard(pray(THICK_SKIN, BURST_OF_STRENGTH))
        body.refused = THICK_SKIN
        hurt()

        guard.act()
        val changes = guard.prayed()
        body.health = Health(20, 20)
        guard.act()

        assertEquals(listOf(PrayerChange(1, BURST_OF_STRENGTH, on = true)), changes)
        assertEquals(listOf("pray thick_skin", "pray burst_of_strength", "stop burst_of_strength"), body.done)
    }

    @Test
    fun `once the When stops holding, what the reflex lit goes out, the step going on`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        guard.act()
        guard.prayed()
        body.health = Health(20, 20)

        guard.act()

        assertEquals(listOf(PrayerChange(1, THICK_SKIN, on = false)), guard.prayed())
        assertEquals(emptySet<CombatPrayer>(), body.lit)
        assertEquals(listOf("fight:1"), log)
    }

    @Test
    fun `a held prayer to put out lets the guard act while the step is busy`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        guard.act()
        body.health = Health(20, 20)
        step.busy = true

        assertFalse(guard.isBusy())
    }

    @Test
    fun `a release alone leaves a busy step's action going`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        guard.act()
        body.health = Health(20, 20)
        step.busy = true

        guard.act()

        assertEquals(listOf("pray thick_skin", "stop thick_skin"), body.done)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun `a release comes before another reflex's lighting in one act, and is said first`() {
        val guard = guard(pray(THICK_SKIN), pray(PROTECT_FROM_MELEE, number = 2, trigger = ReflexTrigger.UnderAttack))
        hurt()
        guard.act()
        guard.prayed()
        body.health = Health(20, 20)
        body.attacked = true

        guard.act()

        assertEquals(listOf(PrayerChange(1, THICK_SKIN, on = false), PrayerChange(2, PROTECT_FROM_MELEE, on = true)), guard.prayed())
        assertEquals(setOf(PROTECT_FROM_MELEE), body.lit)
    }

    @Test
    fun `a prayer on before the reflex wanted it is the player's and stays on`() {
        val guard = guard(pray(THICK_SKIN))
        body.lit += THICK_SKIN
        hurt()
        guard.act()
        body.health = Health(20, 20)
        step.busy = true

        assertTrue(guard.isBusy())
        guard.act()

        assertEquals(setOf(THICK_SKIN), body.lit)
        assertEquals(emptyList<PrayerChange>(), guard.prayed())
    }

    @Test
    fun `a held prayer put out by hand while wanted is lit again`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        guard.act()
        body.lit -= THICK_SKIN

        guard.act()

        assertEquals(listOf("pray thick_skin", "pray thick_skin"), body.done)
    }

    @Test
    fun `a held prayer that went out elsewhere and is no longer wanted is let go without a word`() {
        val guard = guard(pray(THICK_SKIN))
        hurt()
        guard.act()
        guard.prayed()
        body.lit -= THICK_SKIN
        body.health = Health(20, 20)

        guard.act()

        assertEquals(emptyList<PrayerChange>(), guard.prayed())
        assertEquals(listOf("pray thick_skin", "stop thick_skin"), body.done)
    }

    @Test
    fun `a prayer another reflex whose When holds wants stays on`() {
        val guard = guard(pray(THICK_SKIN, number = 1), pray(THICK_SKIN, number = 2, trigger = ReflexTrigger.UnderAttack))
        hurt()
        body.attacked = true
        guard.act()
        body.health = Health(20, 20)

        guard.act()

        assertEquals(setOf(THICK_SKIN), body.lit)
        assertEquals(listOf(PrayerChange(1, THICK_SKIN, on = true)), guard.prayed())
    }

    @Test
    fun `the hold spans the steps' guards, so the next step puts out what no reflex of its wants`() {
        guard(pray(THICK_SKIN)).also { hurt(); it.act() }
        val next = guard()

        next.act()

        assertEquals(listOf(PrayerChange(1, THICK_SKIN, on = false)), next.prayed())
        assertEquals(listOf("pray thick_skin", "stop thick_skin"), body.done)
    }

    @Test
    fun `while the player runs clear held prayers stay on, wanted or not`() {
        val guard = guard(pray(THICK_SKIN), stop(trigger = ReflexTrigger.UnderAttack))
        hurt()
        body.attacked = true
        guard.act()
        guard.act()
        body.health = Health(20, 20)

        guard.act()

        assertEquals(listOf("pray thick_skin", "flee", "flee"), body.done)
        assertEquals(setOf(THICK_SKIN), body.lit)
    }

    @Test
    fun `a stop records the skill level at firing`() {
        body.levels[5] = 0
        val guard = guard(stop(trigger = ReflexTrigger.SkillBelow(5, 1)))

        guard.act()

        assertEquals(StepStop.Reflex(2, Fired.AtSkill(5, 0)), guard.stopping())
    }

}
