package game.idle.movement

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DoorwaysTest {

    /** A door in a wall running north to south, between 3200 and 3201 on y 3200. */
    private val west = Position(3200, 3200)
    private val east = Position(3201, 3200)
    private val door = Doorway(1530, east, EdgeType.DOOR)

    /** A gate in a fence running west to east, between 3210 and 3211 on x 3210. */
    private val south = Position(3210, 3210)
    private val north = Position(3210, 3211)
    private val gate = Doorway(1551, north, EdgeType.GATE)

    private val doorways = Doorways(mapOf((west to east) to door, (south to north) to gate))

    private val outside = WebWalkNode("outside", west, NodeKind.OBSTACLE)
    private val inside = WebWalkNode("inside", east, NodeKind.OBSTACLE)
    private val opens = EdgeAction(objectId = 1530, objectPosition = east)

    /** A tunnel's mouth and its landing on the same floor, and a cave below the mouth. */
    private val mouth = WebWalkNode("mouth", Position(3224, 9600), NodeKind.OBSTACLE)
    private val landing = WebWalkNode("landing", Position(3224, 9604), NodeKind.OBSTACLE)
    private val below = WebWalkNode("below", Position(3224, 9600, 1), NodeKind.OBSTACLE)
    private val squeezes = EdgeAction(objectId = 6912, objectPosition = Position(3224, 9601))
    private val tunnel = Doorway(6912, Position(3224, 9601), EdgeType.ENTRANCE)

    private fun graphOf(vararg edges: WebWalkEdge) =
        WebWalkGraph(listOf(outside, inside, mouth, landing, below), edges.toList(), emptyList(), emptyList())

    @Test
    fun `a doorway is between its two tiles`() {
        assertEquals(door, doorways.between(west, east))
    }

    @Test
    fun `a doorway is between its two tiles the other way across too`() {
        assertEquals(door, doorways.between(east, west))
    }

    @Test
    fun `beside a doorway but not across it there is none`() {
        assertNull(doorways.between(west, Position(3200, 3201)))
    }

    @Test
    fun `away from every doorway there is none`() {
        assertNull(doorways.between(Position(3100, 3100), Position(3101, 3100)))
    }

    @Test
    fun `a step east through the door crosses it`() {
        assertEquals(door, doorways.crossedBy(west, east))
    }

    @Test
    fun `a step north through the gate crosses it`() {
        assertEquals(gate, doorways.crossedBy(south, north))
    }

    @Test
    fun `a diagonal step whose first leg sideways goes through crosses it`() {
        assertEquals(door, doorways.crossedBy(west, Position(3201, 3201)))
    }

    @Test
    fun `a diagonal step whose second leg sideways goes through crosses it`() {
        assertEquals(gate, doorways.crossedBy(Position(3209, 3210), north))
    }

    @Test
    fun `a diagonal step whose first leg ahead goes through crosses it`() {
        assertEquals(gate, doorways.crossedBy(south, Position(3211, 3211)))
    }

    @Test
    fun `a diagonal step whose second leg ahead goes through crosses it`() {
        assertEquals(door, doorways.crossedBy(Position(3200, 3201), east))
    }

    @Test
    fun `a diagonal step beside a doorway, not through it, crosses none`() {
        assertNull(doorways.crossedBy(west, Position(3199, 3201)))
    }

    @Test
    fun `a step far from every doorway crosses none`() {
        assertNull(doorways.crossedBy(Position(3100, 3100), Position(3101, 3101)))
    }

    @Test
    fun `a door edge of the web is a doorway between its nodes`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("outside", "inside", EdgeType.DOOR, action = opens)))

        assertEquals(Doorway(1530, east, EdgeType.DOOR), doorways.between(west, east))
    }

    @Test
    fun `a walk edge is no doorway`() {
        assertNull(Doorways.of(graphOf(WebWalkEdge("outside", "inside", EdgeType.WALK, action = opens))).between(west, east))
    }

    @Test
    fun `a door edge from a node the web lacks is left out`() {
        assertNull(Doorways.of(graphOf(WebWalkEdge("gone", "inside", EdgeType.DOOR, action = opens))).between(west, east))
    }

    @Test
    fun `a door edge to a node the web lacks is left out`() {
        assertNull(Doorways.of(graphOf(WebWalkEdge("outside", "gone", EdgeType.DOOR, action = opens))).between(west, east))
    }

    @Test
    fun `a door edge with nothing to click is left out`() {
        assertNull(Doorways.of(graphOf(WebWalkEdge("outside", "inside", EdgeType.DOOR))).between(west, east))
    }

    @Test
    fun `a door edge naming no object is left out`() {
        val edge = WebWalkEdge("outside", "inside", EdgeType.DOOR, action = EdgeAction(objectPosition = east))

        assertNull(Doorways.of(graphOf(edge)).between(west, east))
    }

    @Test
    fun `a door edge naming no tile for its object is left out`() {
        val edge = WebWalkEdge("outside", "inside", EdgeType.DOOR, action = EdgeAction(objectId = 1530))

        assertNull(Doorways.of(graphOf(edge)).between(west, east))
    }

    /** A double door in a wall running north to south, its leaves on y 3220 and 3221, crossed from x 3220 to 3221. */
    private fun leaf(y: Int, id: Int, type: EdgeType = EdgeType.DOOR) = Doorway(id, Position(3221, y), type)

    private fun wallOf(vararg leaves: Doorway) = Doorways(leaves.associateBy { Position(3220, it.position.y) to it.position })

    @Test
    fun `a single door is its own one leaf`() {
        assertEquals(setOf(door), doorways.leavesOf(door))
    }

    @Test
    fun `a double door shuts with its other leaf`() {
        val left = leaf(3220, 1516)
        val right = leaf(3221, 1519)

        assertEquals(setOf(left, right), wallOf(left, right).leavesOf(left))
    }

    @Test
    fun `a gate shuts with its other leaf, along a fence running west to east`() {
        val hinge = Doorway(1551, Position(3230, 3231), EdgeType.GATE)
        val far = Doorway(1553, Position(3231, 3231), EdgeType.GATE)
        val fence = Doorways(mapOf((Position(3230, 3230) to hinge.position) to hinge, (Position(3231, 3230) to far.position) to far))

        assertEquals(setOf(hinge, far), fence.leavesOf(far))
    }

    @Test
    fun `two doors of one id side by side open apart`() {
        val first = leaf(3220, 1530)

        assertEquals(setOf(first), wallOf(first, leaf(3221, 1530)).leavesOf(first))
    }

    @Test
    fun `a gate beside a door is no leaf of it`() {
        val first = leaf(3220, 1516)

        assertEquals(setOf(first), wallOf(first, leaf(3221, 1553, EdgeType.GATE)).leavesOf(first))
    }

    @Test
    fun `a doorway the web does not know is its own one leaf`() {
        val unknown = Doorway(1530, Position(3300, 3300), EdgeType.DOOR)

        assertEquals(setOf(unknown), doorways.leavesOf(unknown))
    }

    @Test
    fun `an entrance edge of the web is an entrance used from its first node`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("mouth", "landing", EdgeType.ENTRANCE, cost = 5, action = squeezes)))

        assertEquals(listOf(EntranceStep(tunnel, mouth.position, landing.position, ticks = 5.0)), doorways.entrancesFrom(mouth.position))
    }

    @Test
    fun `an entrance edge that says no time takes the planner's`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("mouth", "landing", EdgeType.ENTRANCE, action = squeezes)))

        assertEquals(listOf(4.0), doorways.entrancesFrom(mouth.position).map { it.ticks })
    }

    @Test
    fun `an entrance to another floor is left out`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("mouth", "below", EdgeType.ENTRANCE, cost = 2, action = squeezes)))

        assertEquals(emptyList<EntranceStep>(), doorways.entrancesFrom(mouth.position))
    }

    @Test
    fun `an entrance edge with nothing to click is left out`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("mouth", "landing", EdgeType.ENTRANCE, cost = 5)))

        assertEquals(emptyList<EntranceStep>(), doorways.entrancesFrom(mouth.position))
    }

    @Test
    fun `an entrance is no doorway between side-by-side tiles`() {
        val doorways = Doorways.of(graphOf(WebWalkEdge("mouth", "landing", EdgeType.ENTRANCE, cost = 5, action = squeezes)))

        assertNull(doorways.crossedBy(mouth.position, mouth.position.translate(0, 1)))
    }

    @Test
    fun `no entrance is used from where the web has none`() {
        assertEquals(emptyList<EntranceStep>(), doorways.entrancesFrom(west))
    }
}
