package game.idle.autopilot.travel

import game.idle.flow.Structure
import game.idle.flow.UnderWay
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.idle.movement.FleeRoom
import game.idle.movement.Step
import io.luna.game.model.Direction
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlightTest {

    private val here = Position(3200, 3200)
    private val attacker = listOf(Position(3197, 3200))

    /** The open leaf Luna's doors put beside the room's door when it opens, clicked from inside. */
    private val openLeaf = OpenLeaf(1531, Position(3203, 3201), clickedFrom = listOf(Position(3202, 3200)))

    private class RoomTerrain(private val room: FleeRoom, private val leaf: OpenLeaf?) : FlightTerrain {
        override val doorways: Doorways = room.doorways

        override fun step(from: Position, direction: Direction): Step? = room.step(from, direction)

        override fun npcStep(from: Position, direction: Direction): Boolean = room.npcStep(from, direction)

        override fun onto(from: Position, direction: Direction): Boolean = room.npcStep(from, direction)

        override fun openLeaf(door: Doorway): OpenLeaf? = leaf
    }

    private val traveller = FakeTraveller(here)
    private val flight = Flight(traveller)

    private val shutRoom = FleeRoom()
    private val shut = RoomTerrain(shutRoom, leaf = null)
    private val door = shutRoom.door
    private val closed = door.id to door.position

    private fun openRoom(leaf: OpenLeaf? = openLeaf) = RoomTerrain(FleeRoom(shut = false), leaf)

    /** The flight went through the open door and the player now stands just behind it. */
    private fun through(terrain: FlightTerrain = openRoom()): FlightTerrain {
        flight.flee(attacker, terrain)
        traveller.position = door.position
        return terrain
    }

    @Test
    fun `a flight opens a shut door on its way to shelter`() {
        traveller.standing += closed

        flight.flee(attacker, shut)

        assertEquals(listOf(Triple(door.id, door.position, null)) to UnderWay.Opening(Structure.DOOR), traveller.used to flight.underWay())
    }

    @Test
    fun `a flight is busy while the door it opens still stands shut and the click lands`() {
        traveller.standing += closed
        flight.flee(attacker, shut)
        traveller.busy = true

        assertTrue(flight.isBusy())
    }

    @Test
    fun `a flight on the run is not busy, so the next decision looks again`() {
        flight.flee(attacker, openRoom())

        assertFalse(flight.isBusy())
    }

    @Test
    fun `a door the flight opened is gone through at the next decision`() {
        traveller.standing += closed
        flight.flee(attacker, shut)
        traveller.standing -= closed

        flight.flee(attacker, openRoom())

        assertEquals(UnderWay.Passing(Structure.DOOR), flight.underWay())
    }

    @Test
    fun `a door the flight clicked that stayed shut is tried once more`() {
        traveller.standing += closed
        flight.flee(attacker, shut)

        flight.flee(attacker, shut)

        assertEquals(2 to UnderWay.Opening(Structure.DOOR), traveller.used.size to flight.underWay())
    }

    @Test
    fun `a door the flight clicked twice that stayed shut is not tried again`() {
        traveller.standing += closed
        flight.flee(attacker, shut)
        flight.flee(attacker, shut)

        flight.flee(attacker, shut)

        assertEquals(2 to null, traveller.used.size to flight.underWay())
    }

    @Test
    fun `a door the flight could not click is not tried again`() {
        traveller.standing += closed
        traveller.usable = false
        flight.flee(attacker, shut)

        flight.flee(attacker, shut)

        assertEquals(1, traveller.used.size)
    }

    @Test
    fun `a door the flight left stuck is tried again on the next flight`() {
        traveller.standing += closed
        traveller.usable = false
        flight.flee(attacker, shut)
        flight.calm()

        flight.flee(attacker, shut)

        assertEquals(2, traveller.used.size)
    }

    @Test
    fun `an open door on the way is gone through`() {
        flight.flee(attacker, openRoom())

        assertEquals(listOf(door.position) to UnderWay.Passing(Structure.DOOR), traveller.walks to flight.underWay())
    }

    @Test
    fun `a door far ahead is walked to first`() {
        traveller.position = Position(3194, 3200)

        flight.flee(listOf(Position(3191, 3200)), shut)

        assertEquals(listOf(shutRoom.outside) to null, traveller.walks to flight.underWay())
    }

    @Test
    fun `through the door with every attacker beyond it, the flight shuts it`() {
        val terrain = through()

        flight.flee(attacker, terrain)

        assertEquals(listOf(Triple(openLeaf.id, openLeaf.position, null)) to UnderWay.Closing(Structure.DOOR), traveller.used to flight.underWay())
    }

    @Test
    fun `a door is shut from a tile on the player's side`() {
        val terrain = through(openRoom(openLeaf.copy(clickedFrom = listOf(shutRoom.outside, Position(3203, 3200)))))

        flight.flee(attacker, terrain)

        assertEquals(listOf(Position(3203, 3200)), traveller.sides)
    }

    @Test
    fun `a leaf clicked only from the attackers' side is left open`() {
        val terrain = through(openRoom(openLeaf.copy(clickedFrom = listOf(shutRoom.outside))))

        flight.flee(attacker, terrain)

        assertTrue(traveller.used.isEmpty())
    }

    @Test
    fun `a flight is busy while the click that shuts the door lands`() {
        val terrain = through()
        flight.flee(attacker, terrain)
        traveller.busy = true

        assertTrue(flight.isBusy())
    }

    @Test
    fun `a door is shut only once the player is through it`() {
        val terrain = openRoom()
        flight.flee(attacker, terrain)

        flight.flee(attacker, terrain)

        assertEquals(emptyList<Triple<Int, Position, String?>>() to UnderWay.Passing(Structure.DOOR), traveller.used to flight.underWay())
    }

    @Test
    fun `with an attacker on the player's side the door is left open`() {
        val terrain = through()

        flight.flee(attacker + Position(3204, 3201), terrain)

        assertTrue(traveller.used.isEmpty())
    }

    @Test
    fun `with no open leaf to click the flight runs on`() {
        val terrain = through(openRoom(leaf = null))

        flight.flee(attacker, terrain)

        assertEquals(emptyList<Triple<Int, Position, String?>>() to 2, traveller.used to traveller.walks.size)
    }

    @Test
    fun `a shutting click that cannot be made leaves the door open and runs on`() {
        val terrain = through()
        traveller.usable = false

        flight.flee(attacker, terrain)

        assertFalse(flight.underWay() is UnderWay.Closing)
    }

    @Test
    fun `a door the flight shut is told once the flight is over`() {
        val terrain = through()
        flight.flee(attacker, terrain)
        traveller.standing += closed

        flight.calm()

        assertEquals(Structure.DOOR, flight.shut())
    }

    @Test
    fun `a door shut behind the player is told only once`() {
        val terrain = through()
        flight.flee(attacker, terrain)
        traveller.standing += closed
        flight.calm()
        flight.shut()

        assertNull(flight.shut())
    }

    @Test
    fun `a door the flight clicked that still stands open is not told`() {
        val terrain = through()
        flight.flee(attacker, terrain)

        flight.calm()

        assertNull(flight.shut())
    }

    @Test
    fun `with nothing to run from the flight stands`() {
        flight.flee(emptyList(), openRoom())

        assertEquals(emptyList<Position>() to null, traveller.walks to flight.underWay())
    }

    @Test
    fun `once calm the flight has nothing on its way`() {
        flight.flee(attacker, openRoom())

        flight.calm()

        assertNull(flight.underWay())
    }

    @Test
    fun `before any flight only a click under way holds the player`() {
        traveller.busy = true

        assertTrue(flight.isBusy())
    }
}
