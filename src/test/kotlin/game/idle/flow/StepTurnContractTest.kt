package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * The rules every kind of step shares, proved on each kind's real work over its fake adapter (step behaviour refactor,
 * 2026-10-11). A kind joins by adding its [KindFixture]; the kind's own tests keep to what is its own.
 */
class StepTurnContractTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKinds")
    fun `before it acts a turn has done nothing, worked nothing and reports nothing`(kind: KindFixture) {
        val turn = kind.turn()

        val reported = listOf(turn.amountDone(), turn.worked(), turn.isDone(), turn.blocked(), turn.waiting(), turn.stopping(), turn.said())

        assertEquals(listOf(0, false, false, null, null, null, null), reported)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKinds")
    fun `a turn ended early is over at its next decision`(kind: KindFixture) {
        val turn = kind.turn()
        turn.endTurn()

        turn.act()

        assertTrue(turn.isDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allKinds")
    fun `a turn ended early is not busy while nothing settles`(kind: KindFixture) {
        val turn = kind.turn()

        turn.endTurn()

        assertFalse(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("workingKinds")
    fun `the first decision with something to do is work`(kind: KindFixture) {
        val turn = kind.turn()

        turn.act()

        assertEquals(listOf(true, false, null, null), listOf(turn.worked(), turn.isDone(), turn.blocked(), turn.waiting()))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countingKinds")
    fun `the count is what rose since the turn began`(kind: KindFixture) {
        val turn = kind.turn()
        turn.act()

        kind.gain(2)

        assertEquals(2, turn.amountDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countingKinds")
    fun `what rose before the turn began is not counted`(kind: KindFixture) {
        kind.gain(3)

        val turn = kind.turn()

        assertEquals(0, turn.amountDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countingKinds")
    fun `the count never falls`(kind: KindFixture) {
        val turn = kind.turn()
        kind.gain(2)
        turn.amountDone()

        kind.gain(-1)

        assertEquals(2, turn.amountDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `the amount ends the step, breaking off the work`(kind: GatherFixture) {
        val turn = kind.turn(amount = 2)
        turn.act()
        kind.gain(2)

        turn.act()

        assertEquals(true to "interrupt", turn.isDone() to kind.done.last())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `the amount reached, the step is not busy however busy the adapter is`(kind: GatherFixture) {
        val turn = kind.turn(amount = 1)
        turn.act()
        kind.adapterBusy(true)

        kind.gain(1)

        assertFalse(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `short of the amount the step is busy while the adapter is`(kind: GatherFixture) {
        val turn = kind.turn(amount = 2)
        turn.act()
        kind.adapterBusy(true)

        kind.gain(1)

        assertTrue(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `without its tool the step blocks on the tool before it looks`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.carried.bag -= kind.tool.items.keys

        turn.act()

        assertEquals(Reason.NeedsTool(kind.tool) to emptyList<String>(), turn.blocked() to kind.done)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `the block clears once the tool is back and the step works`(kind: GatherFixture) {
        val turn = kind.turn()
        val tools = kind.tool.items.keys
        kind.carried.bag -= tools
        turn.act()
        kind.carried.bag += tools

        turn.act()

        assertEquals(null to true, turn.blocked() to turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `below its level the step blocks on the level`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.carried.levels[kind.level.skill] = kind.level.level - 1

        turn.act()

        assertEquals(Reason.NeedsLevel(kind.level.skill, kind.level.level), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `the tool is asked for before the level`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.carried.bag -= kind.tool.items.keys
        kind.carried.levels[kind.level.skill] = kind.level.level - 1

        turn.act()

        assertEquals(Reason.NeedsTool(kind.tool), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a full bag at the start ends a step without an amount silently`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.carried.full = true

        turn.act()

        assertEquals(listOf(true, null, emptyList<String>()), listOf(turn.isDone(), turn.said(), kind.done))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a full bag at the start ends a step with an amount, saying the bag is full`(kind: GatherFixture) {
        val turn = kind.turn(amount = 5)
        kind.carried.full = true

        turn.act()

        assertEquals(true to Said.Ended(Reason.BagFull(emptyList())), turn.isDone() to turn.said())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a full bag after working ends a step without an amount silently`(kind: GatherFixture) {
        val turn = kind.turn()
        turn.act()
        kind.carried.full = true

        turn.act()

        assertEquals(true to null, turn.isDone() to turn.said())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `what the step says is given once`(kind: GatherFixture) {
        val turn = kind.turn(amount = 5)
        kind.carried.full = true
        turn.act()
        turn.said()

        assertNull(turn.said())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `inside the area with nothing in reach the step waits for the world, not blocked`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.clearTargets()

        turn.act()

        assertEquals(listOf(true, null, emptyList<String>()), listOf(turn.waiting() is Reason.NoneInReach, turn.blocked(), kind.done))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `the wait clears once a target is back`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.clearTargets()
        turn.act()
        kind.restoreTargets()

        turn.act()

        assertEquals(null to true, turn.waiting() to turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `outside the area with nothing in reach the step walks back to the work spot`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()

        turn.act()

        assertEquals(listOf<Any?>(1, null, null), listOf(kind.walker.walks, turn.blocked(), turn.waiting()))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a walk back that gets nowhere blocks on the work spot`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()
        turn.act()

        turn.act()

        assertEquals(Reason.NoWay(Place.WORK_SPOT), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a work spot on another floor blocks the walk back`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()
        kind.walker.here = kind.walker.here.copy(z = 1)

        turn.act()

        assertEquals(Reason.OtherFloor(Place.WORK_SPOT), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `back at the work spot the kind looks again`(kind: GatherFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()
        turn.act()
        kind.walker.here = kind.anchor
        kind.enterArea()
        kind.restoreTargets()
        turn.act()

        turn.act()

        assertTrue(turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a target tried twice with nothing gained is set aside, and with none other in reach the step blocks`(kind: GatherFixture) {
        val turn = kind.turn()
        turn.act()

        turn.act()

        assertEquals(1 to true, kind.done.size to (turn.blocked() is Reason.NoneInReach))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("gatherKinds")
    fun `a target tried again after a gain is not a retry`(kind: GatherFixture) {
        val turn = kind.turn()
        turn.act()
        kind.gain(1)

        turn.act()

        assertEquals(2, kind.done.size)
    }

    companion object {

        @JvmStatic
        fun allKinds(): List<KindFixture> = plainKinds() + gatherKinds()

        /** Every kind but walk: a walk approaches, it never works. */
        @JvmStatic
        fun workingKinds(): List<KindFixture> = allKinds().filter { it.name != "walk" }

        @JvmStatic
        fun countingKinds(): List<KindFixture> = plainKinds().filter { it.name == "rest" } + gatherKinds()

        @JvmStatic
        fun gatherKinds(): List<GatherFixture> = game.idle.flow.gatherKinds()
    }
}
