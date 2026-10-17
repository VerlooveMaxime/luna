package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import game.idle.autopilot.Candidate
import game.idle.flow.Passage
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkUpsTest {

    private val here = Position(3200, 3200)
    private val approach = Position(3204, 3200)

    /** A door one tile east of the player, crossed from 3201 to 3202. */
    private fun doorOfType(type: EdgeType) =
        DoorStep(Doorway(1530, Position(3202, 3200), type), from = Position(3201, 3200), to = Position(3202, 3200), shut = true)

    private val door = doorOfType(EdgeType.DOOR)
    private val shut = 1530 to Position(3202, 3200)

    private fun tree(doors: List<DoorStep>, id: Int = 1276) =
        Candidate(id, Position(3205, 3200), distance = 12, usableFromHere = false, approach, doors = doors)

    private val traveller = FakeTraveller(here)
    private val walkUps = WalkUps(traveller)

    @Test
    fun `with no walk up under way the kind's own busy rule holds`() {
        assertEquals(true to false, walkUps.isBusy(kindBusy = true) to walkUps.isBusy(kindBusy = false))
    }

    @Test
    fun `a walk up under way keeps the kind's busy rule, not every action the player has queued`() {
        walkUps.walkTo(tree(emptyList()))
        traveller.busy = true

        assertFalse(walkUps.isBusy(kindBusy = false))
    }

    @Test
    fun `a walk up under way is busy while the kind is`() {
        walkUps.walkTo(tree(emptyList()))

        assertTrue(walkUps.isBusy(kindBusy = true))
    }

    /** A door twelve tiles east of the player, so the walk up first walks to it. */
    private val farDoor = DoorStep(Doorway(1530, Position(3213, 3200), EdgeType.DOOR), Position(3212, 3200), Position(3213, 3200), shut = true)

    @Test
    fun `a walk up in the last tiles before the door on its way clicks on, however busy the kind is`() {
        walkUps.walkTo(tree(listOf(farDoor)))
        traveller.position = Position(3208, 3200)

        assertFalse(walkUps.isBusy(kindBusy = true))
    }

    @Test
    fun `a walk up with no door on the way walks to the tile the target is used from`() {
        val outcome = walkUps.walkTo(tree(emptyList()))

        assertEquals(StepOutcome.Approached to listOf(approach), outcome to traveller.walks)
    }

    @Test
    fun `a walk up opens a shut door on the way first`() {
        traveller.standing += shut

        val outcome = walkUps.walkTo(tree(listOf(door)))

        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)) to listOf(Triple(1530, Position(3202, 3200), null)), outcome to traveller.used)
    }

    @Test
    fun `a walk up goes through an open door on the way`() {
        val outcome = walkUps.walkTo(tree(listOf(door)))

        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.DOOR)) to listOf(Position(3202, 3200)), outcome to traveller.walks)
    }

    @Test
    fun `a walk up whose door cannot be clicked is left to the retry rule, not a block`() {
        traveller.standing += shut
        traveller.usable = false

        assertEquals(StepOutcome.Approached, walkUps.walkTo(tree(listOf(door))))
    }

    @Test
    fun `a walk up already on its tile is a walk like any other, not the end of the step`() {
        traveller.position = approach

        assertEquals(StepOutcome.Approached, walkUps.walkTo(tree(emptyList())))
    }

    @Test
    fun `stopping ends the walk up under way, and its clicking on`() {
        walkUps.walkTo(tree(listOf(farDoor)))
        traveller.position = Position(3208, 3200)

        walkUps.stop()

        assertTrue(walkUps.isBusy(kindBusy = true))
    }

    @Test
    fun `a target set aside while its walk up opened a door tells that door`() {
        traveller.standing += shut
        val target = tree(listOf(door))
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertEquals(Said.DoorShut(Passage.DOOR, Tile(3202, 3200)), walkUps.said())
    }

    @Test
    fun `a target set aside at a door that could not be clicked tells that door`() {
        traveller.standing += shut
        traveller.usable = false
        val target = tree(listOf(door))
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertEquals(Said.DoorShut(Passage.DOOR, Tile(3202, 3200)), walkUps.said())
    }

    @Test
    fun `a gate is told as a gate`() {
        traveller.standing += shut
        val target = tree(listOf(doorOfType(EdgeType.GATE)))
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertEquals(Said.DoorShut(Passage.GATE, Tile(3202, 3200)), walkUps.said())
    }

    @Test
    fun `a curtain is told as a curtain`() {
        traveller.standing += shut
        val target = tree(listOf(doorOfType(EdgeType.CURTAIN)))
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertEquals(Said.DoorShut(Passage.CURTAIN, Tile(3202, 3200)), walkUps.said())
    }

    @Test
    fun `a door is told once a turn`() {
        traveller.standing += shut
        val target = tree(listOf(door))
        walkUps.walkTo(target)
        walkUps.setAside(target.aim)
        walkUps.said()
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertNull(walkUps.said())
    }

    @Test
    fun `a target set aside after a walk up through an open door tells nothing`() {
        val target = tree(listOf(door))
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertNull(walkUps.said())
    }

    @Test
    fun `a target set aside after a walk up with no door tells nothing`() {
        val target = tree(emptyList())
        walkUps.walkTo(target)

        walkUps.setAside(target.aim)

        assertNull(walkUps.said())
    }

    @Test
    fun `another target set aside tells nothing of the door`() {
        traveller.standing += shut
        walkUps.walkTo(tree(listOf(door)))

        walkUps.setAside(tree(emptyList(), id = 1278).aim)

        assertNull(walkUps.said())
    }

    @Test
    fun `a target set aside once the walk up was stopped tells nothing`() {
        traveller.standing += shut
        val target = tree(listOf(door))
        walkUps.walkTo(target)
        walkUps.stop()

        walkUps.setAside(target.aim)

        assertNull(walkUps.said())
    }

    @Test
    fun `the route walks to each door's near side, through it and on to the target`() {
        val second = DoorStep(Doorway(1531, Position(3203, 3201), EdgeType.GATE), from = Position(3203, 3200), to = Position(3203, 3201), shut = false)

        val legs = WalkUps.plan(here, tree(listOf(door, second))).legs

        val expected = listOf(
            Triple(EdgeType.WALK, here, Position(3201, 3200)),
            Triple(EdgeType.DOOR, Position(3201, 3200), Position(3202, 3200)),
            Triple(EdgeType.WALK, Position(3202, 3200), Position(3203, 3200)),
            Triple(EdgeType.GATE, Position(3203, 3200), Position(3203, 3201)),
            Triple(EdgeType.WALK, Position(3203, 3201), approach),
        )
        assertEquals(expected, legs.map { Triple(it.type, it.from, it.to) })
    }

    @Test
    fun `a door leg names the door's object, to click`() {
        val leg = WalkUps.plan(here, tree(listOf(door))).legs[1]

        assertEquals(1530 to Position(3202, 3200), leg.action?.objectId to leg.action?.objectPosition)
    }

    @Test
    fun `from a door's near side the route starts with the door`() {
        val legs = WalkUps.plan(Position(3201, 3200), tree(listOf(door))).legs

        assertTrue(legs.first().type == EdgeType.DOOR)
    }
}
