package game.idle.flow

import engine.combat.prayer.CombatPrayer.THICK_SKIN
import game.idle.movement.TravelChoice
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowRunnerTest {

    private val player = FakeFlowPlayer()
    private val body = player.body
    private val low = Health(4, 20)
    private val noAxe = Reason.NeedsTool(ToolNeed("axe", mapOf(1351 to 1), Skill.WOODCUTTING))
    private val stuck = StepStop.Stuck(Reason.NoneLeftInBank(1521))

    private val clock = FakeClock()
    private val edgeville = TeleportDestination.Jewellery(TeleportJewellery.AMULET_OF_GLORY, 0)

    private fun runner(vararg names: String, startAt: Int = 0) = runner(ResolvedFlow(names.map { FakeStep(it) }), startAt)

    private fun runner(flow: ResolvedFlow, startAt: Int = 0, stop: FlowStop = FlowStop.Never, progress: RunProgress = RunProgress(), laps: Int = 0) =
        FlowRunner(flow, startAt, player, words(), clock, stop, progress, laps)

    /** A flow of [names] whose first step has a reflex that jumps to the step at [to], running clear first while attacked. */
    private fun jumping(vararg names: String, to: Int): FlowRunner {
        val jump = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.JumpTo(to))
        val steps = names.map { FakeStep(it) }
        return runner(ResolvedFlow(steps, listOf(listOf(jump)) + steps.drop(1).map { emptyList() }))
    }

    /** A flow of [names] whose first step has a reflex that teleports to Varrock, then stops or jumps to the step at [to]. */
    private fun teleporting(vararg names: String, to: Int? = null): FlowRunner {
        val varrock = TeleportDestination.Spell(TeleportSpell.VARROCK)
        body.teleportable += varrock
        val teleport = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.Teleport(varrock, to))
        val steps = names.map { FakeStep(it) }
        return runner(ResolvedFlow(steps, listOf(listOf(teleport)) + steps.drop(1).map { emptyList() }))
    }

    /** The first step's reflex fires on the next act. */
    private fun hurt() {
        body.health = low
    }

    private fun healed() {
        body.health = Health(20, 20)
    }

    @Test
    fun `the first act starts the first step and acts on it`() {
        runner("chop").act()

        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `a step keeps acting until it is over`() {
        val runner = runner("chop")

        repeat(3) { runner.act() }

        assertEquals(listOf("chop:1", "chop:2", "chop:3"), player.log)
    }

    @Test
    fun `busy follows the current step`() {
        val runner = runner("chop")
        assertFalse(runner.isBusy())
        runner.act()

        player.started.single().busy = true

        assertTrue(runner.isBusy())
    }

    @Test
    fun `the current step's reason to stop is the flow's, worded with the step's number`() {
        val runner = runner("walk", "bank", startAt = 1)
        runner.act()
        player.started.single().stop = stuck

        runner.act()

        assertEquals("Autopilot: stopped, step 2 is stuck: no item 1521 left in the bank.", runner.stopReason())
    }

    @Test
    fun `a reflex that stops the flow is worded with its number and what it fired at`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = StepStop.Reflex(2, Fired.AtHitpoints(Health(9, 20)))

        runner.act()

        assertEquals("Autopilot: stopped, reflex 2 fired at 9/20 hitpoints.", runner.stopReason())
    }

    @Test
    fun `a stop while the player is attacked waits, the player running`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true

        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf("flee"), body.done)
    }

    @Test
    fun `once nothing attacks a stop that waited stops the flow, the player's running back as before`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()
        body.attacked = false

        runner.act()

        assertEquals("Autopilot: stopped, step 1 is stuck: no item 1521 left in the bank.", runner.stopReason())
        assertEquals(listOf("flee", "calm"), body.done)
    }

    @Test
    fun `while a stop waits the flow waits for the player to run clear`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        player.started.single().waiting = Reason.NoneInReach(Target.NPC)
        body.attacked = true

        runner.act()

        assertEquals("Running clear before stopping", runner.waiting())
    }

    @Test
    fun `while a stop waits the step's block is not shown`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        player.started.single().blocked = noAxe
        body.attacked = true

        runner.act()

        assertEquals(null, runner.blocked())
    }

    @Test
    fun `once clear the flow no longer waits to run clear`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()
        body.attacked = false

        runner.act()

        assertEquals(null, runner.waiting())
    }

    @Test
    fun `a flow waiting to stop acts at each decision, its step busy or not`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().busy = true

        player.started.single().stop = stuck

        assertFalse(runner.isBusy())
    }

    @Test
    fun `a flow waiting to stop no longer works its step`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true

        runner.act()

        assertEquals(listOf("fight:1"), player.log)
    }

    @Test
    fun `the current step's block is the flow's, in words`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().blocked = noAxe

        assertEquals("Needs an axe you can use", runner.blocked())
    }

    @Test
    fun `what the current step waits for is the flow's, in the block's words`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().waiting = Reason.NoneInReach(Target.TREE)

        assertEquals("No tree in reach", runner.waiting())
    }

    @Test
    fun `what the current step's trip does on its way is the flow's, in words`() {
        val runner = runner("chop")
        runner.act()

        player.started.single().underWay = UnderWay.Opening(Passage.DOOR)

        assertEquals("Opening the door", runner.underWay())
    }

    @Test
    fun `nothing is under way between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().underWay = UnderWay.Opening(Passage.DOOR)
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.underWay())
    }

    @Test
    fun `while a stop waits what the step's trip did on its way is not shown`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        player.started.single().underWay = UnderWay.Opening(Passage.DOOR)
        body.attacked = true

        runner.act()

        assertEquals(null, runner.underWay())
    }

    @Test
    fun `while a stop waits what the flight does on its way is the flow's, in words`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()

        body.flightUnderWay = UnderWay.Closing(Passage.DOOR)

        assertEquals("Closing the door", runner.underWay())
    }

    @Test
    fun `while a stop waits the flow holds its next decision as the flight clicks a door`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()

        body.flightBusy = true

        assertTrue(runner.isBusy())
    }

    @Test
    fun `while a stop waits on a plain run the flow acts at each decision`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()

        player.started.single().busy = true

        assertFalse(runner.isBusy())
    }

    @Test
    fun `a door the flight shut behind the player is said before the flow stops`() {
        val runner = runner("fight")
        runner.act()
        player.started.single().stop = stuck
        body.attacked = true
        runner.act()
        body.attacked = false
        body.shutBehind = Passage.DOOR

        runner.act()

        assertEquals(listOf<Any?>(listOf("Autopilot: closed the door behind you."), "Autopilot: stopped, step 1 is stuck: no item 1521 left in the bank."), listOf(player.told, runner.stopReason()))
    }

    @Test
    fun `a door the flight shut behind the player is said before the jump it led to`() {
        hurt()
        body.attacked = true
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        body.attacked = false
        body.shutBehind = Passage.GATE

        runner.act()

        assertEquals(listOf("Autopilot: closed the gate behind you.", "Autopilot: reflex 1 fired at 4/20 hitpoints, jumping to step 2."), player.told)
    }

    @Test
    fun `nothing is waited for between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().waiting = Reason.NoneInReach(Target.TREE)
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.waiting())
    }

    @Test
    fun `nothing is blocked between two steps`() {
        val runner = runner("chop", "drop")
        runner.act()
        player.started.single().blocked = noAxe
        player.started.single().done = true

        runner.act()

        assertEquals(null, runner.blocked())
    }

    @Test
    fun `what a step says after it acts is told, worded with its number`() {
        val runner = runner("fight", "pickup", startAt = 1)
        runner.act()
        player.started.single().said = Said.Ended(Reason.BagFull(listOf(526)))

        runner.act()

        assertEquals(listOf("Autopilot: step 2 ended, the bag is full: item 526 left lying."), player.told)
    }

    @Test
    fun `a step that says nothing tells nothing`() {
        val runner = runner("fight")

        runner.act()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `a long way round is told once a run for each Travel choice`() {
        val runner = runner("walk")
        runner.act()
        player.started.single().said = Said.LongWay(TravelChoice.COINS)
        runner.act()
        player.started.single().said = Said.LongWay(TravelChoice.COINS)
        runner.act()
        player.started.single().said = Said.LongWay(TravelChoice.RUNES)

        runner.act()

        assertEquals(
            listOf(
                "Autopilot: step 1 takes the long way: tolls and fares are off in Travel.",
                "Autopilot: step 1 takes the long way: teleport spells are off in Travel.",
            ),
            player.told,
        )
    }

    @Test
    fun `a long way round told is saved with the run`() {
        val runner = runner("walk")
        runner.act()
        player.started.single().said = Said.LongWay(TravelChoice.CHARGES)

        runner.act()

        assertEquals(setOf(TravelChoice.CHARGES), player.savedProgress.last().toldLongWay)
    }

    @Test
    fun `a run resumed after a relog does not tell a long way round again`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("walk"))), progress = RunProgress(toldLongWay = setOf(TravelChoice.COINS)))
        runner.act()
        player.started.single().said = Said.LongWay(TravelChoice.COINS)

        runner.act()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `what a step's trips say is told at once, worded with its number`() {
        val runner = runner("walk", "bank", startAt = 1)
        runner.act()

        player.speeches.last().say(Said.PaidToll(10))

        assertEquals(listOf("Autopilot: step 2 pays the 10-coin toll at the Al Kharid gate."), player.told)
    }

    @Test
    fun `a long way round a trip tells at once is told once a run too`() {
        val runner = runner("walk")
        runner.act()

        player.speeches.last().say(Said.LongWay(TravelChoice.RUNES))
        player.speeches.last().say(Said.LongWay(TravelChoice.RUNES))

        assertEquals(1, player.told.size)
    }

    @Test
    fun `a trip's spend is told every time`() {
        val runner = runner("walk")
        runner.act()
        player.started.single().said = Said.PaidToll(10)
        runner.act()
        player.started.single().said = Said.PaidToll(10)

        runner.act()

        assertEquals(2, player.told.size)
    }

    @Test
    fun `a step over before its turn says nothing`() {
        val runner = runner("bank", "fight")
        runner.act()
        player.started.single().done = true
        player.started.single().said = Said.Skipped(Reason.BankFull)

        runner.act()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `a flow that has not started a step has no reason to stop`() {
        assertEquals(null, runner("fight").stopReason())
    }

    @Test
    fun `a step that says it is done hands over to the next`() {
        val runner = runner("chop", "bank")
        runner.act()
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop:1", "chop:2", "bank:1"), player.log)
        assertEquals(listOf(1), player.savedSteps)
    }

    @Test
    fun `a step after the first runs its own activity`() {
        runner("chop", "drop", startAt = 1).act()

        assertEquals(listOf("drop:1"), player.log)
    }

    @Test
    fun `after the last step the flow starts over with a fresh activity`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("bank:1", "chop:1"), player.log)
        assertEquals(listOf(0), player.savedSteps)
    }

    @Test
    fun `starting over counts a lap`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(1, player.laps)
    }

    @Test
    fun `a lap in which no step worked stops the flow instead of counting`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].worked = false
        player.started[0].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP to 0, runner.stopReason() to player.laps)
    }

    @Test
    fun `a lap in which a step worked is counted and the flow goes on`() {
        val runner = runner("chop", "bank", startAt = 1)
        runner.act()
        player.started[0].worked = true
        player.started[0].done = true

        runner.act()

        assertEquals(null to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `work earlier in the lap counts for it`() {
        val runner = runner("chop", "bank")
        runner.act()
        player.started[0].done = true
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(1, player.laps)
    }

    @Test
    fun `each lap is judged on its own`() {
        val runner = runner("chop")
        runner.act()
        player.started[0].done = true
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `work on a step a jump cut short counts for the lap`() {
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        hurt()
        runner.act()
        healed()
        runner.act()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(null to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `a step cut short before it worked counts for nothing`() {
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        player.started[0].worked = false
        hurt()
        runner.act()
        healed()
        runner.act()
        player.started[1].worked = false
        player.started[1].done = true

        runner.act()

        assertEquals(StepWords.IDLE_LAP, runner.stopReason())
    }

    @Test
    fun `moving to a step that is not the first counts no lap`() {
        val runner = runner("chop", "bank")
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(0, player.laps)
    }

    @Test
    fun `a one step flow repeats itself`() {
        val runner = runner("chop")
        runner.act()
        player.started[0].done = true

        runner.act()
        runner.act()

        assertEquals(listOf("chop:1", "chop:1"), player.log)
        assertEquals(2, player.started.size)
    }

    @Test
    fun `a saved step past the end starts at the last step`() {
        runner("chop", "bank", startAt = 7).act()

        assertEquals(listOf("bank:1"), player.log)
    }

    @Test
    fun `a negative saved step starts at the first`() {
        runner("chop", startAt = -3).act()

        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `a flow starting at a saved step resumes there`() {
        runner("chop", "bank", startAt = 1).act()

        assertEquals(listOf("bank:1"), player.log)
    }

    @Test
    fun `an empty flow does nothing`() {
        val runner = runner()

        runner.act()

        assertFalse(runner.isBusy())
        assertEquals(emptyList<String>(), player.log)
    }

    @Test
    fun `a step's reflexes guard it`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()

        runner.act()

        assertEquals(emptyList<String>(), player.log)
    }

    @Test
    fun `a step without reflexes acts at any hitpoints`() {
        val runner = runner("fight")
        hurt()

        runner.act()

        assertEquals(listOf("fight:1"), player.log)
    }

    @Test
    fun `a reflex's jump goes on from the step it names`() {
        val runner = jumping("fight", "walk", "bank", to = 2)
        hurt()
        runner.act()

        runner.act()

        assertEquals(listOf("bank:1"), player.log)
        assertEquals(listOf(2), player.savedSteps)
    }

    @Test
    fun `a teleport's stop follows the landing, worded as any reflex stop`() {
        hurt()
        val runner = teleporting("fight")
        runner.act()
        runner.act()
        body.teleporting = false

        runner.act()

        assertEquals(listOf<Any?>("Autopilot: stopped, reflex 1 fired at 4/20 hitpoints.", listOf("teleport varrock", "calm", "calm")), listOf(runner.stopReason(), body.done))
    }

    @Test
    fun `mid-flight the flow neither stops nor moves on`() {
        hurt()
        val runner = teleporting("fight", "bank")
        runner.act()

        runner.act()

        assertEquals(listOf<Any?>(null, emptyList<Int>(), listOf("teleport varrock")), listOf(runner.stopReason(), player.savedSteps, body.done))
    }

    @Test
    fun `a teleport's jump goes on from its step once landed, saved and said`() {
        hurt()
        val runner = teleporting("fight", "bank", to = 1)
        runner.act()
        body.teleporting = false

        runner.act()

        assertEquals(listOf<Any>(listOf("Autopilot: reflex 1 fired at 4/20 hitpoints, jumping to step 2."), listOf(1)), listOf(player.told, player.savedSteps))
    }

    @Test
    fun `a jump says why`() {
        hurt()

        jumping("fight", "bank", to = 1).act()

        assertEquals(listOf("Autopilot: reflex 1 fired at 4/20 hitpoints, jumping to step 2."), player.told)
    }

    @Test
    fun `a jump back to the first step counts no lap`() {
        val runner = jumping("fight", "bank", to = 0)
        hurt()

        runner.act()

        assertEquals(0, player.laps)
        assertEquals(listOf(0), player.savedSteps)
    }

    @Test
    fun `a step cut short again before it made progress stops the flow`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()

        runner.act()

        assertEquals("Autopilot: stopped, step 1 was cut short again before it made progress.", runner.stopReason())
    }

    @Test
    fun `a step cut short again stops the flow without jumping`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()

        runner.act()

        assertEquals(listOf(1, 0), player.savedSteps)
        assertEquals(1, player.told.size)
    }

    @Test
    fun `a step that made progress before it was cut short again goes on`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()
        healed()
        runner.act()
        player.started[2].amountDone = 1
        hurt()

        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf(1, 0, 1), player.savedSteps)
    }

    @Test
    fun `a step that ended by itself since it was cut short starts afresh`() {
        val runner = jumping("fight", "bank", to = 1)
        hurt()
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()
        healed()
        runner.act()
        player.started[2].done = true
        runner.act()
        runner.act()
        player.started[3].done = true
        runner.act()
        hurt()

        runner.act()

        assertEquals(null, runner.stopReason())
    }

    /** A flow of [names] whose steps in [praying] have a reflex that prays Thick skin below half hitpoints. */
    private fun praying(vararg names: String, praying: Set<Int>): FlowRunner {
        val pray = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.Pray(listOf(THICK_SKIN)))
        val steps = names.map { FakeStep(it) }
        return runner(ResolvedFlow(steps, steps.indices.map { if (it in praying) listOf(pray) else emptyList() }))
    }

    @Test
    fun `a special attack lit is said`() {
        body.special = WornSpecial(100, attack = false)
        hurt()
        val special = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.SpecialAttack)
        val steps = listOf(FakeStep("fight"))

        runner(ResolvedFlow(steps, listOf(listOf(special)))).act()

        assertEquals(listOf("Autopilot: reflex 1 used the special attack."), player.told)
    }

    @Test
    fun `a reflex that took worn jewellery off to rub it says so`() {
        hurt()
        body.wornJewellery = true
        body.teleportable += edgeville
        val teleport = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.Teleport(edgeville, jumpTo = null))
        val steps = listOf(FakeStep("fight"))

        runner(ResolvedFlow(steps, listOf(listOf(teleport)))).act()

        assertEquals(listOf("Autopilot: reflex 1 takes off the item 1712 to rub it to Edgeville."), player.told)
    }

    @Test
    fun `a reflex that could not wear its jewellery again says so`() {
        hurt()
        body.notWornAgain = true
        body.teleportable += edgeville
        val teleport = ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.Teleport(edgeville, jumpTo = null))
        val steps = listOf(FakeStep("fight"))

        runner(ResolvedFlow(steps, listOf(listOf(teleport)))).act()

        assertEquals(listOf("Autopilot: reflex 1 could not wear the item 1712 again."), player.told)
    }

    @Test
    fun `what a reflex lit is said`() {
        hurt()

        praying("fight", praying = setOf(0)).act()

        assertEquals(listOf("Autopilot: reflex 1 prays Thick skin."), player.told)
    }

    @Test
    fun `a held prayer stays on into a step whose reflex still wants it`() {
        val runner = praying("fight", "walk", praying = setOf(0, 1))
        hurt()
        runner.act()
        player.started.single().done = true
        runner.act()

        runner.act()

        assertEquals(listOf("Autopilot: reflex 1 prays Thick skin."), player.told)
        assertEquals(setOf(THICK_SKIN), body.lit)
    }

    @Test
    fun `a held prayer goes out in a step with no reflex wanting it`() {
        val runner = praying("fight", "walk", praying = setOf(0))
        hurt()
        runner.act()
        player.started.single().done = true
        runner.act()

        runner.act()

        assertEquals(listOf("Autopilot: reflex 1 prays Thick skin.", "Autopilot: reflex 1 stops praying Thick skin."), player.told)
        assertEquals(emptySet<Any>(), body.lit)
    }

    @Test
    fun `the run's end puts out what its reflexes lit and says so`() {
        val runner = praying("fight", praying = setOf(0))
        hurt()
        runner.act()

        runner.end()

        assertEquals(listOf("Autopilot: reflex 1 prays Thick skin.", "Autopilot: reflex 1 stops praying Thick skin."), player.told)
        assertEquals(emptySet<Any>(), body.lit)
    }

    @Test
    fun `the end of a run that lit nothing says nothing`() {
        val runner = runner("fight")
        runner.act()

        runner.end()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `a reflex that stops the flow stops it`() {
        val stop = ResolvedReflex(2, ReflexTrigger.HitpointsBelow(50), ReflexAction.StopFlow)
        val runner = runner(ResolvedFlow(listOf(FakeStep("fight")), listOf(listOf(stop))))
        hurt()

        runner.act()

        assertEquals("Autopilot: stopped, reflex 2 fired at 4/20 hitpoints.", runner.stopReason())
    }

    @Test
    fun `the player's death stops the flow`() {
        val runner = runner("fight")
        runner.act()

        body.dead = true

        assertEquals(StepWords.DIED, runner.stopReason())
    }

    @Test
    fun `a flow resolved without reflexes has none for each step`() {
        assertEquals(listOf(emptyList<ResolvedReflex>()), ResolvedFlow(listOf(FakeStep("chop"))).reflexes)
    }

    @Test
    fun `a flow resolved with reflexes for some steps only is refused`() {
        assertThrows<IllegalArgumentException> { ResolvedFlow(listOf(FakeStep("chop"), FakeStep("drop")), listOf(emptyList())) }
    }

    private val strength20 = ResolvedGoal(Goal.Level(Skill.STRENGTH, 20), WhenMet.SKIP)
    private val count3 = ResolvedGoal(Goal.Count(3), WhenMet.SKIP)

    /** A flow of [names] whose first step has [goal], under [stop], resuming [progress] after [laps] laps. */
    private fun goaled(vararg names: String, goal: ResolvedGoal?, stop: FlowStop = FlowStop.Never, progress: RunProgress = RunProgress(), laps: Int = 0): FlowRunner {
        val steps = names.map { FakeStep(it) }
        return runner(ResolvedFlow(steps, goals = listOf(goal) + steps.drop(1).map { null }), stop = stop, progress = progress, laps = laps)
    }

    @Test
    fun `a step whose goal is met before it starts is skipped with a line`() {
        player.levels[Skill.STRENGTH] = 20

        goaled("fight", "bank", goal = strength20).act()

        assertEquals(listOf("bank:1"), player.log)
        assertEquals(listOf("Autopilot: step 1 met its goal, Strength 20: skipped for this run."), player.told)
    }

    @Test
    fun `a goal met is saved as met`() {
        player.levels[Skill.STRENGTH] = 20

        goaled("fight", "bank", goal = strength20).act()

        assertEquals(setOf(0), player.savedProgress.last().met)
    }

    @Test
    fun `a step met for the run is skipped on every later lap`() {
        val runner = goaled("fight", "bank", goal = strength20, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(listOf("bank:1") to emptyList<String>(), player.log to player.told)
    }

    @Test
    fun `a goal reached mid-turn makes the flow not busy, so the next decision comes at once`() {
        val runner = goaled("fight", goal = strength20)
        runner.act()
        player.started.single().busy = true
        player.levels[Skill.STRENGTH] = 20

        assertFalse(runner.isBusy())
    }

    @Test
    fun `a goal reached mid-turn ends the turn before the step acts again`() {
        val runner = goaled("fight", goal = strength20)
        runner.act()
        player.levels[Skill.STRENGTH] = 20

        runner.act()

        assertEquals(listOf("fight:1", "fight:end", "fight:2"), player.log)
    }

    @Test
    fun `a turn ended for its goal is busy while its unit settles, and ended once`() {
        val runner = goaled("fight", goal = strength20)
        runner.act()
        player.levels[Skill.STRENGTH] = 20
        runner.act()
        player.started.single().busy = true

        val busy = runner.isBusy()
        runner.act()

        assertEquals(true to 1, busy to player.started.single().ended)
    }

    @Test
    fun `the time up makes the flow not busy`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterMinutes(5), progress = RunProgress(startedAt = 1_000))
        runner.act()
        player.started.single().busy = true
        clock.now = 1_000 + 5 * 60_000

        assertFalse(runner.isBusy())
    }

    @Test
    fun `a goal that stops the flow at the last step is not overruled by the lap guard`() {
        player.levels[Skill.STRENGTH] = 20
        val steps = listOf(FakeStep("chop"), FakeStep("fight"))
        val runner = runner(ResolvedFlow(steps, goals = listOf(null, ResolvedGoal(Goal.Level(Skill.STRENGTH, 20), WhenMet.STOP))), startAt = 1)
        runner.act()

        assertEquals("Autopilot: stopped, step 2 met its goal: Strength 20." to 0, runner.stopReason() to player.laps)
    }

    @Test
    fun `a goal met as the last step's turn ends is written once with its count`() {
        val runner = goaled("chop", goal = count3)
        runner.act()
        player.started[0].amountDone = 3
        player.started[0].done = true

        runner.act()

        assertEquals(listOf(RunProgress(totals = mapOf(0 to 3), met = setOf(0))), player.savedProgress)
    }

    @Test
    fun `passing met steps saves no step index, a relog passing them again`() {
        val runner = goaled("fight", "bank", goal = strength20, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(emptyList<Int>(), player.savedSteps)
    }

    @Test
    fun `a goal reached as the step ends is met then`() {
        val runner = goaled("fight", "bank", goal = strength20)
        runner.act()
        player.levels[Skill.STRENGTH] = 20
        player.started.single().done = true

        runner.act()

        assertEquals(listOf("Autopilot: step 1 met its goal, Strength 20: skipped for this run."), player.told)
        assertEquals(0, player.started.single().ended)
    }

    @Test
    fun `a count goal adds up what every turn counted`() {
        val runner = goaled("chop", "bank", goal = count3)
        runner.act()
        player.started[0].amountDone = 2
        player.started[0].done = true
        runner.act()
        runner.act()
        player.started[1].done = true
        runner.act()
        runner.act()
        player.started[2].amountDone = 1

        runner.act()

        assertEquals(1, player.started[2].ended)
    }

    @Test
    fun `what a turn counted is saved for the run`() {
        val runner = goaled("chop", "bank", goal = count3)
        runner.act()
        player.started[0].amountDone = 2
        player.started[0].done = true

        runner.act()

        assertEquals(listOf(RunProgress(totals = mapOf(0 to 2))), player.savedProgress)
    }

    @Test
    fun `a turn that counted nothing saves nothing`() {
        val runner = runner("chop", "bank")
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(emptyList<RunProgress>(), player.savedProgress)
    }

    @Test
    fun `a bank goal reads what the bank holds`() {
        player.bank[1521] = 60
        player.bank[1522] = 40
        val bank = ResolvedGoal(Goal.Bank(1521, setOf(1521, 1522), 100), WhenMet.SKIP)

        goaled("chop", "bank", goal = bank).act()

        assertEquals(listOf("bank:1"), player.log)
    }

    @Test
    fun `a bank goal short of its amount lets the step run`() {
        player.bank[1521] = 99
        val bank = ResolvedGoal(Goal.Bank(1521, setOf(1521), 100), WhenMet.SKIP)

        goaled("chop", "bank", goal = bank).act()

        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `a lap in which a step worked before every step was met is counted, and the next stops the flow`() {
        player.levels[Skill.STRENGTH] = 20
        val steps = listOf(FakeStep("chop"), FakeStep("fight"))
        val runner = runner(ResolvedFlow(steps, goals = listOf(count3, strength20)))
        runner.act()
        player.started[0].amountDone = 3
        player.started[0].done = true
        runner.act()
        runner.act()

        val afterLap = runner.stopReason() to player.laps
        runner.act()

        assertEquals(listOf(null to 1, StepWords.IDLE_LAP), listOf(afterLap, runner.stopReason()))
    }

    @Test
    fun `a time goal counts the minutes since the run started`() {
        val runner = goaled("chop", goal = ResolvedGoal(Goal.Time(30), WhenMet.SKIP), progress = RunProgress(startedAt = 60_000))
        runner.act()
        clock.now = 60_000 + 29 * 60_000
        runner.act()
        clock.now = 60_000 + 30 * 60_000

        runner.act()

        assertEquals(1, player.started.single().ended)
    }

    @Test
    fun `a goal that stops the flow stops it in its words`() {
        player.levels[Skill.STRENGTH] = 20

        val runner = goaled("fight", "bank", goal = ResolvedGoal(Goal.Level(Skill.STRENGTH, 20), WhenMet.STOP))
        runner.act()

        assertEquals("Autopilot: stopped, step 1 met its goal: Strength 20.", runner.stopReason())
        assertEquals(emptyList<String>() to emptyList<String>(), player.log to player.told)
    }

    @Test
    fun `a goal's stop waits while the player is attacked`() {
        player.levels[Skill.STRENGTH] = 20
        body.attacked = true

        val runner = goaled("fight", goal = ResolvedGoal(Goal.Level(Skill.STRENGTH, 20), WhenMet.STOP))
        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf("flee"), body.done)
    }

    @Test
    fun `once every goal is met the flow stops when asked to`() {
        player.levels[Skill.STRENGTH] = 20

        val runner = goaled("fight", "bank", goal = strength20, stop = FlowStop.AllGoalsMet)
        runner.act()

        assertEquals(StepWords.ALL_GOALS_MET, runner.stopReason())
    }

    @Test
    fun `a goal still to meet keeps the flow going`() {
        player.levels[Skill.STRENGTH] = 20
        val steps = listOf(FakeStep("fight"), FakeStep("chop"))

        val runner = runner(ResolvedFlow(steps, goals = listOf(strength20, count3)), stop = FlowStop.AllGoalsMet)
        runner.act()

        assertEquals(null, runner.stopReason())
        assertEquals(listOf("chop:1"), player.log)
    }

    @Test
    fun `without that stop every goal met leaves the flow going`() {
        player.levels[Skill.STRENGTH] = 20

        val runner = goaled("fight", "bank", goal = strength20)
        runner.act()

        assertEquals(null, runner.stopReason())
    }

    @Test
    fun `a flow with every step met stops as an idle lap`() {
        val runner = goaled("fight", "bank", goal = strength20, progress = RunProgress(met = setOf(0, 1)))

        runner.act()

        assertEquals(StepWords.IDLE_LAP, runner.stopReason())
        assertEquals(emptyList<String>(), player.log)
    }

    @Test
    fun `after the laps asked for the flow stops`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterLaps(1))
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals("Autopilot: stopped after 1 lap." to 1, runner.stopReason() to player.laps)
    }

    @Test
    fun `the laps already run count toward the stop`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterLaps(3), laps = 2)
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals("Autopilot: stopped after 3 laps.", runner.stopReason())
    }

    @Test
    fun `before the laps asked for the flow goes on`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterLaps(2))
        runner.act()
        player.started[0].done = true

        runner.act()

        assertEquals(null, runner.stopReason())
    }

    @Test
    fun `after the minutes asked for the flow stops without acting`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterMinutes(5), progress = RunProgress(startedAt = 1_000))
        clock.now = 1_000 + 5 * 60_000

        runner.act()

        assertEquals("Autopilot: stopped after 5 minutes.", runner.stopReason())
        assertEquals(emptyList<String>(), player.log)
    }

    @Test
    fun `before the minutes asked for the flow goes on`() {
        val runner = runner(ResolvedFlow(listOf(FakeStep("chop"))), stop = FlowStop.AfterMinutes(5), progress = RunProgress(startedAt = 1_000))
        clock.now = 1_000 + 5 * 60_000 - 1

        runner.act()

        assertEquals(listOf("chop:1"), player.log)
    }

    private val light = FakeStep("light", needs = listOf(StepNeeds(inputs = listOf(1521))))
    private val bank = FakeBankStep(withdraws = setOf(1521, 1511))

    @Test
    fun `a bank step leaves out the withdrawals that feed only skipped steps`() {
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(listOf(setOf(1521)), player.leftOut)
    }

    @Test
    fun `a bank step tells what it leaves out and for which step`() {
        val bank = FakeBankStep(withdraws = setOf(1521, 1511), amounts = mapOf(1521 to 5))
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(listOf("Autopilot: step 2 leaves out 5 item 1521, step 1 met its goal."), player.told)
    }

    @Test
    fun `a bank step leaving out all of a withdrawal names no amount`() {
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(listOf("Autopilot: step 2 leaves out item 1521, step 1 met its goal."), player.told)
    }

    @Test
    fun `a bank step tells what it leaves out once a run`() {
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = RunProgress(met = setOf(0)))
        runner.act()
        player.started.single().done = true
        runner.act()

        runner.act()

        assertEquals(1, player.told.size)
    }

    @Test
    fun `what a bank step told it leaves out is saved with the run`() {
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = RunProgress(met = setOf(0)))

        runner.act()

        assertEquals(mapOf(1 to setOf(1521)), player.savedProgress.last().toldLeftOut)
    }

    @Test
    fun `a run resumed after a relog does not tell again what it left out`() {
        val told = RunProgress(met = setOf(0), toldLeftOut = mapOf(1 to setOf(1521)))
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1, progress = told)

        runner.act()

        assertEquals(emptyList<String>(), player.told)
    }

    @Test
    fun `a bank step with no step skipped withdraws everything`() {
        val runner = runner(ResolvedFlow(listOf(light, bank)), startAt = 1)

        runner.act()

        assertEquals(listOf(emptySet<Int>()), player.leftOut)
    }

    @Test
    fun `a jump folds what the step counted into the run`() {
        val runner = jumping("fight", "bank", to = 1)
        runner.act()
        player.started[0].amountDone = 2
        hurt()
        runner.act()
        healed()

        runner.act()

        assertEquals(mapOf(0 to 2), player.savedProgress.last().totals)
    }

    @Test
    fun `a flow resolved with goals for some steps only is refused`() {
        assertThrows<IllegalArgumentException> { ResolvedFlow(listOf(FakeStep("chop"), FakeStep("bank")), goals = listOf(null)) }
    }
}
