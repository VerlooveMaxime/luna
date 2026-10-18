package game.idle.movement

import api.bot.webwalk.model.EdgeType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShutOutTest {

    private val here = Position(3200, 3200)
    private val west = Position(3197, 3200)

    @Test
    fun `a shut door keeps out an attacker on its far side`() {
        val room = FleeRoom()

        assertTrue(room.shutOut(here, listOf(west)).keepsOut(room.door, room.inside))
    }

    @Test
    fun `an open door would keep out an attacker on its far side once shut`() {
        val room = FleeRoom(shut = false)

        assertTrue(room.shutOut(here, listOf(west)).keepsOut(room.door, room.inside))
    }

    @Test
    fun `a door keeps out an attacker right behind it once shut`() {
        val room = FleeRoom(shut = false)

        assertTrue(room.shutOut(here, listOf(room.outside)).keepsOut(room.door, room.door.position))
    }

    @Test
    fun `a door keeps no one out on its own side`() {
        val room = FleeRoom(shut = false)

        assertFalse(room.shutOut(here, listOf(west, Position(3204, 3201))).keepsOut(room.door, room.inside))
    }

    @Test
    fun `a door keeps no one out of a room with another way in`() {
        val room = FleeRoom(shut = false, backWay = true)

        assertFalse(room.shutOut(here, listOf(west)).keepsOut(room.door, room.inside))
    }

    @Test
    fun `an attacker on another floor is none to keep out`() {
        val room = FleeRoom(shut = false)

        assertTrue(room.shutOut(here, listOf(Position(3201, 3200, 1))).keepsOut(room.door, room.inside))
    }

    /** A wall along the line between x 3200 and 3201 whose one way through is a double door on y 3200 and 3201, open. */
    private fun doubleDoor(rightId: Int): Pair<Doorway, ShutOut> {
        val left = Doorway(1516, Position(3201, 3200), EdgeType.DOOR)
        val right = Doorway(rightId, Position(3201, 3201), EdgeType.DOOR)
        val doorways = Doorways(mapOf((Position(3200, 3200) to left.position) to left, (Position(3200, 3201) to right.position) to right))
        val npcStep = { from: Position, direction: Direction ->
            val to = from.translate(1, direction)
            (from.x <= 3200) == (to.x <= 3200) || doorways.crossedBy(from, to) != null
        }
        return left to ShutOut(here, listOf(Position(3195, 3200)), npcStep, npcStep, doorways)
    }

    @Test
    fun `a double door shuts both its leaves`() {
        val (left, shutOut) = doubleDoor(rightId = 1519)

        assertTrue(shutOut.keepsOut(left, Position(3205, 3200)))
    }

    @Test
    fun `a door shuts alone beside another of its id, which keeps the way open`() {
        val (left, shutOut) = doubleDoor(rightId = 1516)

        assertFalse(shutOut.keepsOut(left, Position(3205, 3200)))
    }
}
