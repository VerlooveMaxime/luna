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
    @MethodSource("countingKinds")
    fun `the amount ends the step`(kind: KindFixture) {
        val turn = kind.turn(amount = 2)
        turn.act()
        kind.gain(2)

        turn.act()

        assertTrue(turn.isDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("promptKinds")
    fun `the amount ends the step, breaking off the work`(kind: AmountFixture) {
        val turn = kind.turn(amount = 2)
        turn.act()
        kind.gain(2)

        turn.act()

        assertEquals(true to "interrupt", turn.isDone() to kind.done.last())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("promptKinds")
    fun `the amount reached, the step is not busy however busy the adapter is`(kind: AmountFixture) {
        val turn = kind.turn(amount = 1)
        turn.act()
        kind.adapterBusy(true)

        kind.gain(1)

        assertFalse(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settlingKinds")
    fun `the amount reached, the step is busy while its unit settles`(kind: SettlingFixture) {
        val turn = kind.turn(amount = 1)
        turn.act()
        kind.gain(1)

        kind.settling(true)

        assertTrue(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("settlingKinds")
    fun `the amount reached, the step ends once its unit settled`(kind: SettlingFixture) {
        val turn = kind.turn(amount = 1)
        turn.act()
        kind.gain(1)
        kind.settling(true)
        turn.isBusy()
        kind.settling(false)

        turn.act()

        assertTrue(turn.isDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("amountKinds")
    fun `short of the amount the step is busy while the adapter is`(kind: AmountFixture) {
        val turn = kind.turn(amount = 2)
        turn.act()
        kind.adapterBusy(true)

        kind.gain(1)

        assertTrue(turn.isBusy())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("toolKinds")
    fun `without its tool the step blocks on the tool before it looks`(kind: ToolFixture) {
        val turn = kind.turn()
        kind.carried.bag -= kind.tool.items.keys

        turn.act()

        assertEquals(Reason.NeedsTool(kind.tool) to emptyList<String>(), turn.blocked() to kind.done)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("toolKinds")
    fun `the block clears once the tool is back and the step works`(kind: ToolFixture) {
        val turn = kind.turn()
        val tools = kind.tool.items.keys
        kind.carried.bag -= tools
        turn.act()
        kind.carried.bag += tools

        turn.act()

        assertEquals(null to true, turn.blocked() to turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("needsKinds")
    fun `below its level the step blocks on the level`(kind: NeedsFixture) {
        val turn = kind.turn()
        kind.carried.levels[kind.level.skill] = kind.level.level - 1

        turn.act()

        assertEquals(Reason.NeedsLevel(kind.level.skill, kind.level.level), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("toolKinds")
    fun `the tool is asked for before the level`(kind: ToolFixture) {
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
    @MethodSource("processKinds")
    fun `nothing to work with ends the step silently, which is no work`(kind: ProcessFixture) {
        kind.emptyInput()
        val turn = kind.turn()

        turn.act()

        assertEquals(listOf(true, false, null, null, emptyList<String>()), listOf(turn.isDone(), turn.worked(), turn.blocked(), turn.said(), kind.done))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("processKinds")
    fun `the step ends once its input runs out after working`(kind: ProcessFixture) {
        val turn = kind.turn()
        turn.act()
        kind.gain(1)
        kind.emptyInput()

        turn.act()

        assertEquals(true to 1, turn.isDone() to kind.done.size)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("processKinds")
    fun `input that arrives during the turn is used`(kind: ProcessFixture) {
        val turn = kind.turn()
        turn.act()
        kind.gain(1)
        kind.emptyInput()
        kind.supply(1)

        turn.act()

        assertEquals(false to 2, turn.isDone() to kind.done.size)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fallingKinds")
    fun `input arriving is not counted`(kind: ProcessFixture) {
        val turn = kind.turn()

        kind.supply(2)

        assertEquals(0, turn.amountDone())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("processKinds")
    fun `a level too low for input not held does not block, the step ends empty`(kind: ProcessFixture) {
        kind.emptyInput()
        kind.carried.levels[kind.level.skill] = kind.level.level - 1
        val turn = kind.turn()

        turn.act()

        assertEquals(true to null, turn.isDone() to turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("waitingKinds")
    fun `inside the area with nothing in reach the step waits for the world, not blocked`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.clearTargets()

        turn.act()

        assertEquals(listOf(true, null, emptyList<String>()), listOf(turn.waiting() is Reason.NoneInReach, turn.blocked(), kind.done))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("waitingKinds")
    fun `the wait clears once a target is back`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.clearTargets()
        turn.act()
        kind.restoreTargets()

        turn.act()

        assertEquals(null to true, turn.waiting() to turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placeKinds")
    fun `inside the area with no place to work at the step blocks, the flow or the player must supply one`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.clearTargets()

        turn.act()

        assertEquals(listOf(true, null, emptyList<String>()), listOf(turn.blocked() is Reason.NoneInReach, turn.waiting(), kind.done))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("placeKinds")
    fun `the block clears once a place is back`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.clearTargets()
        turn.act()
        kind.restoreTargets()

        turn.act()

        assertEquals(null to true, turn.blocked() to turn.worked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaKinds")
    fun `outside the area with nothing in reach the step walks back to the work spot`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()

        turn.act()

        assertEquals(listOf<Any?>(1, null, null), listOf(kind.walker.walks, turn.blocked(), turn.waiting()))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaKinds")
    fun `a walk back that gets nowhere blocks on the work spot`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()
        turn.act()

        turn.act()

        assertEquals(Reason.NoWay(Place.WORK_SPOT), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaKinds")
    fun `a work spot on another floor blocks the walk back`(kind: AreaFixture) {
        val turn = kind.turn()
        kind.leaveArea()
        kind.clearTargets()
        kind.walker.here = kind.walker.here.copy(z = 1)

        turn.act()

        assertEquals(Reason.OtherFloor(Place.WORK_SPOT), turn.blocked())
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaKinds")
    fun `back at the work spot the kind looks again`(kind: AreaFixture) {
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
    @MethodSource("stayingKinds")
    fun `a target tried twice with nothing gained is set aside, and with none other in reach the step blocks`(kind: AreaFixture) {
        val turn = kind.turn()
        turn.act()

        turn.act()

        assertEquals(1 to true, kind.done.size to (turn.blocked() is Reason.NoneInReach))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("areaKinds")
    fun `a target tried again after a gain is not a retry`(kind: AreaFixture) {
        val turn = kind.turn()
        turn.act()
        kind.gain(1)

        turn.act()

        assertEquals(2, kind.done.size)
    }

    companion object {

        @JvmStatic
        fun allKinds(): List<KindFixture> = plainKinds() + gatherKinds() + processKinds() + lootKinds()

        /** Every kind but walk: a walk approaches, it never works. */
        @JvmStatic
        fun workingKinds(): List<KindFixture> = allKinds().filter { it.name != "walk" }

        @JvmStatic
        fun countingKinds(): List<KindFixture> = plainKinds().filter { it.name == "rest" } + amountKinds()

        @JvmStatic
        fun amountKinds(): List<AmountFixture> = gatherKinds() + processKinds() + lootKinds()

        /** Every amount kind but light, whose fire must catch before the step may end and whose own tests cover that. */
        @JvmStatic
        fun promptKinds(): List<AmountFixture> = amountKinds().filter { it.name != "light" }

        /** The amount kinds whose last unit may still have to settle once the Amount is reached: light's fire, fight's last kill. */
        @JvmStatic
        fun settlingKinds(): List<SettlingFixture> = amountKinds().filterIsInstance<SettlingFixture>()

        @JvmStatic
        fun needsKinds(): List<NeedsFixture> = gatherKinds() + processKinds()

        @JvmStatic
        fun toolKinds(): List<ToolFixture> = needsKinds().filterIsInstance<ToolFixture>()

        /** The kinds that work at targets around a work spot: chop, mine, fish, cook, smelt, smith, fight. */
        @JvmStatic
        fun areaKinds(): List<AreaFixture> = amountKinds().filterIsInstance<AreaFixture>()

        /** The area kinds whose targets the world supplies by itself (a tree grows back, an npc respawns): nothing in reach is a wait. */
        @JvmStatic
        fun waitingKinds(): List<AreaFixture> = gatherKinds() + lootKinds().filterIsInstance<AreaFixture>()

        /** The area kinds whose targets stay where they are (objects, spots), so one set aside stays so for the turn; npcs wander and come back. */
        @JvmStatic
        fun stayingKinds(): List<AreaFixture> = areaKinds().filter { it.name != "fight" }

        /** The processing kinds that work at a place (a fire, a furnace, an anvil) the world never supplies by itself. */
        @JvmStatic
        fun placeKinds(): List<AreaFixture> = processKinds().filterIsInstance<AreaFixture>()

        /** The kinds whose meter falls as they work (light, cook): input arriving moves it the other way. */
        @JvmStatic
        fun fallingKinds(): List<ProcessFixture> = processKinds().filter { it.name == "light" || it.name == "cook" }

        @JvmStatic
        fun gatherKinds(): List<GatherFixture> = game.idle.flow.gatherKinds()

        @JvmStatic
        fun processKinds(): List<ProcessFixture> = game.idle.flow.processKinds()

        @JvmStatic
        fun lootKinds(): List<AmountFixture> = game.idle.flow.lootKinds()
    }
}
