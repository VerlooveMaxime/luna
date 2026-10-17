package game.idle.flow

import game.idle.location.Tile
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepTurnTest {

    private val anchor = Tile(3101, 3509)
    private val carried = FakeCarried()
    private val walks = mutableListOf<FakeStepWork>()
    private val axe = ToolNeed("axe", mapOf(1351 to 1), Skill.WOODCUTTING, worn = true)
    private val needsAxe = listOf(StepNeeds(tools = listOf(axe)))

    /** A turn over [work]; every walk back is a [FakeStepWork] that approaches, kept in [walks]. */
    private fun turn(work: FakeStepWork, amount: Int? = null, needs: List<StepNeeds> = emptyList(), fillsBag: Boolean = false, walkBack: StepOutcome = StepOutcome.Approached) =
        StepTurn(work, amount, needs, carried, fillsBag) { FakeStepWork(walkBack, meter = null).also { walks += it } }

    private fun working() = FakeStepWork(StepOutcome.Worked)

    @Test
    fun `busy follows the work`() {
        val work = working()
        work.busy = true

        assertTrue(turn(work).isBusy())
    }

    @Test
    fun `a worked decision marks the turn as worked and clears any block`() {
        val work = FakeStepWork(StepOutcome.Blocked(Reason.NoneInReach(Target.TREE)), StepOutcome.Worked)
        val turn = turn(work)
        turn.act()

        turn.act()

        assertEquals(listOf(true, null, false), listOf(turn.worked(), turn.blocked(), turn.isDone()))
    }

    @Test
    fun `an approach clears the block but is no work`() {
        val work = FakeStepWork(StepOutcome.Blocked(Reason.NoneInReach(Target.TREE)), StepOutcome.Approached)
        val turn = turn(work)
        turn.act()

        turn.act()

        assertEquals(null to false, turn.blocked() to turn.worked())
    }

    @Test
    fun `a block is reported as it is`() {
        val turn = turn(FakeStepWork(StepOutcome.Blocked(Reason.NoneInReach(Target.ROCK))))

        turn.act()

        assertEquals(Reason.NoneInReach(Target.ROCK), turn.blocked())
    }

    @Test
    fun `a wait for the world is reported apart from a block`() {
        val turn = turn(FakeStepWork(StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK))))

        turn.act()

        assertEquals(Reason.NoneInReach(Target.ROCK) to null, turn.waiting() to turn.blocked())
    }

    @Test
    fun `a block ends a wait`() {
        val turn = turn(FakeStepWork(StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK)), StepOutcome.Blocked(Reason.BagFull(emptyList()))))
        turn.act()

        turn.act()

        assertEquals(null to Reason.BagFull(emptyList()), turn.waiting() to turn.blocked())
    }

    @Test
    fun `work ends a wait`() {
        val turn = turn(FakeStepWork(StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK)), StepOutcome.Worked))
        turn.act()

        turn.act()

        assertNull(turn.waiting())
    }

    @Test
    fun `what a trip does on its way is reported apart from a block and a wait`() {
        val turn = turn(FakeStepWork(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR))))

        turn.act()

        assertEquals(Triple(UnderWay.Opening(Passage.DOOR), null, null), Triple(turn.underWay(), turn.blocked(), turn.waiting()))
    }

    @Test
    fun `a trip on its way is no work`() {
        val turn = turn(FakeStepWork(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR))))

        turn.act()

        assertFalse(turn.worked())
    }

    @Test
    fun `a plain walk ends what a trip did on its way`() {
        val turn = turn(FakeStepWork(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)), StepOutcome.Approached))
        turn.act()

        turn.act()

        assertNull(turn.underWay())
    }

    @Test
    fun `a block ends what a trip did on its way`() {
        val turn = turn(FakeStepWork(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)), StepOutcome.Blocked(Reason.NoWay(Place.TILE))))
        turn.act()

        turn.act()

        assertEquals(null to Reason.NoWay(Place.TILE), turn.underWay() to turn.blocked())
    }

    @Test
    fun `a wait ends what a trip did on its way`() {
        val turn = turn(FakeStepWork(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)), StepOutcome.Waiting(Reason.NoneInReach(Target.ROCK))))
        turn.act()

        turn.act()

        assertNull(turn.underWay())
    }

    @Test
    fun `nothing to work with from the flow ends the step silently`() {
        val turn = turn(FakeStepWork(StepOutcome.Empty))

        turn.act()

        assertEquals(listOf(true, null, null), listOf(turn.isDone(), turn.said(), turn.blocked()))
    }

    @Test
    fun `the step's own end ends it`() {
        val turn = turn(FakeStepWork(StepOutcome.Finished))

        turn.act()

        assertTrue(turn.isDone())
    }

    @Test
    fun `ending drops the block`() {
        val turn = turn(FakeStepWork(StepOutcome.Blocked(Reason.NoneInReach(Target.TREE)), StepOutcome.Finished))
        turn.act()

        turn.act()

        assertNull(turn.blocked())
    }

    @Test
    fun `a full bag with nothing left ends a step without an amount silently`() {
        val turn = turn(FakeStepWork(StepOutcome.BagFull(emptyList())))

        turn.act()

        assertEquals(true to null, turn.isDone() to turn.said())
    }

    @Test
    fun `a full bag ends a step with an amount, saying so`() {
        val turn = turn(FakeStepWork(StepOutcome.BagFull(emptyList())), amount = 5)

        turn.act()

        assertEquals(true to Said.Ended(Reason.BagFull(emptyList())), turn.isDone() to turn.said())
    }

    @Test
    fun `a full bag with items left ends the step, saying what was left`() {
        val turn = turn(FakeStepWork(StepOutcome.BagFull(listOf(526))))

        turn.act()

        assertEquals(Said.Ended(Reason.BagFull(listOf(526))), turn.said())
    }

    @Test
    fun `what the step says is given once`() {
        val turn = turn(FakeStepWork(StepOutcome.BagFull(listOf(526))))
        turn.act()
        turn.said()

        assertNull(turn.said())
    }

    @Test
    fun `stuck with the flow to stop reports the stop and keeps the step`() {
        val turn = turn(FakeStepWork(StepOutcome.Stuck(Reason.BankFull, stopsFlow = true)))

        turn.act()

        assertEquals(StepStop.Stuck(Reason.BankFull) to false, turn.stopping() to turn.isDone())
    }

    @Test
    fun `stuck with the turn to skip says so and ends the step`() {
        val turn = turn(FakeStepWork(StepOutcome.Stuck(Reason.NoneLeftInBank(1521), stopsFlow = false)))

        turn.act()

        assertEquals(listOf(Said.Skipped(Reason.NoneLeftInBank(1521)), true, null), listOf(turn.said(), turn.isDone(), turn.stopping()))
    }

    @Test
    fun `away starts the walk back to the spot in the same decision`() {
        val turn = turn(FakeStepWork(StepOutcome.Away(anchor)))

        turn.act()

        assertEquals(listOf("act null"), walks.single().done)
    }

    @Test
    fun `while it walks back the kind is not asked and busy follows the walk`() {
        val work = FakeStepWork(StepOutcome.Away(anchor))
        val turn = turn(work)
        turn.act()
        walks.single().busy = true

        turn.act()

        assertEquals(listOf("act null") to true, work.done to turn.isBusy())
    }

    @Test
    fun `the walk back's block is the step's`() {
        val turn = turn(FakeStepWork(StepOutcome.Away(anchor)), walkBack = StepOutcome.Blocked(Reason.NoWay(Place.WORK_SPOT)))

        turn.act()

        assertEquals(Reason.NoWay(Place.WORK_SPOT), turn.blocked())
    }

    @Test
    fun `what the walk back does on its way is the step's`() {
        val turn = turn(FakeStepWork(StepOutcome.Away(anchor)), walkBack = StepOutcome.OnTheWay(UnderWay.Passing(Passage.STAIRS_UP)))

        turn.act()

        assertEquals(UnderWay.Passing(Passage.STAIRS_UP), turn.underWay())
    }

    @Test
    fun `back at the spot the next decision is the kind's again`() {
        val work = FakeStepWork(StepOutcome.Away(anchor), StepOutcome.Worked)
        val turn = turn(work, walkBack = StepOutcome.Finished)
        turn.act()

        turn.act()

        assertEquals(listOf("act null", "act null") to true, work.done to turn.worked())
    }

    @Test
    fun `a tool lacking blocks before the kind looks`() {
        val work = working()
        val turn = turn(work, needs = needsAxe)

        turn.act()

        assertEquals(Reason.NeedsTool(axe) to emptyList<String>(), turn.blocked() to work.done)
    }

    @Test
    fun `a tool worn counts when Luna counts it worn`() {
        val work = working()
        carried.worn += 1351

        turn(work, needs = needsAxe).act()

        assertEquals(listOf("act null"), work.done)
    }

    @Test
    fun `a full bag on a kind that fills it ends the step before the kind looks`() {
        val work = working()
        carried.full = true
        val turn = turn(work, fillsBag = true)

        turn.act()

        assertEquals(true to emptyList<String>(), turn.isDone() to work.done)
    }

    @Test
    fun `a full bag does not hold a kind that does not fill it`() {
        val work = working()
        carried.full = true

        turn(work).act()

        assertEquals(listOf("act null"), work.done)
    }

    @Test
    fun `a bag with room lets a kind that fills it work`() {
        val work = working()

        turn(work, fillsBag = true).act()

        assertEquals(listOf("act null"), work.done)
    }

    @Test
    fun `the kind is told what the amount still asks for`() {
        val work = working()
        val turn = turn(work, amount = 5)
        turn.act()
        work.reading = 2

        turn.act()

        assertEquals(listOf("act 5", "act 3"), work.done)
    }

    @Test
    fun `the amount reached breaks off the work and ends the step`() {
        val work = working()
        val turn = turn(work, amount = 2)
        turn.act()
        work.reading = 2

        turn.act()

        assertEquals(listOf("act 2", "interrupt") to true, work.done to turn.isDone())
    }

    @Test
    fun `the amount reached, the step is not busy however busy the work is`() {
        val work = working()
        val turn = turn(work, amount = 1)
        work.busy = true
        work.reading = 1

        assertFalse(turn.isBusy())
    }

    @Test
    fun `the amount reached, a unit still settling holds the step`() {
        val work = working()
        val turn = turn(work, amount = 1)
        work.reading = 1
        work.finishing = true

        assertTrue(turn.isBusy())
    }

    @Test
    fun `ending the turn early breaks off the walk back under way`() {
        val work = FakeStepWork(StepOutcome.Away(anchor))
        val turn = turn(work)
        turn.act()
        turn.endTurn()

        turn.act()

        assertEquals(listOf("act null", "interrupt") to listOf("act null"), walks.single().done to work.done)
    }

    @Test
    fun `ending the turn early breaks off the work at the next decision`() {
        val work = working()
        val turn = turn(work)
        turn.act()
        turn.endTurn()

        turn.act()

        assertEquals(listOf("act null", "interrupt") to true, work.done to turn.isDone())
    }

    @Test
    fun `a turn ending early is held only by the unit still settling`() {
        val work = working()
        work.busy = true
        val turn = turn(work)

        turn.endTurn()

        assertFalse(turn.isBusy())
    }

    @Test
    fun `the count starts where the meter stood when the turn was built`() {
        val work = working()
        work.reading = 4
        val turn = turn(work)
        work.reading = 6

        assertEquals(2, turn.amountDone())
    }

    @Test
    fun `the count is sampled on every busy poll, so a rise and a fall do not cancel`() {
        val work = working()
        val turn = turn(work)
        work.reading = 3
        turn.isBusy()
        work.reading = 1

        assertEquals(3, turn.amountDone())
    }

    @Test
    fun `a kind that counts nothing has done nothing however it acts`() {
        val work = FakeStepWork(StepOutcome.Worked, meter = null)
        val turn = turn(work)
        work.reading = 5

        turn.act()

        assertEquals(0, turn.amountDone())
    }

    @Test
    fun `a kind that counts nothing is never done by an amount`() {
        val work = FakeStepWork(StepOutcome.Worked, meter = null)
        val turn = turn(work, amount = 1)
        work.reading = 5

        turn.act()

        assertEquals(listOf("act 1") to false, work.done to turn.isDone())
    }

    @Test
    fun `without an amount the kind is told nothing is asked for`() {
        val work = working()

        turn(work).act()

        assertEquals(listOf("act null"), work.done)
    }

    @Test
    fun `a turn never stops the flow or speaks on its own`() {
        val turn = turn(working())

        turn.act()

        assertEquals(null to null, turn.stopping() to turn.said())
    }
}
