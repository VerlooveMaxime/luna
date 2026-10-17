package game.idle.movement

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.ticksForTiles
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.Random

class TravelWebTest {

    private val start = Position(98, 100)
    private val pastTheDoor = Position(122, 100)

    private val west = WebWalkNode("west", Position(100, 100), NodeKind.WAYPOINT)
    private val doorWest = WebWalkNode("door_west", Position(110, 100), NodeKind.OBSTACLE)
    private val doorEast = WebWalkNode("door_east", Position(111, 100), NodeKind.OBSTACLE)
    private val east = WebWalkNode("east", Position(120, 100), NodeKind.WAYPOINT)
    private val far = WebWalkNode("far", Position(400, 400), NodeKind.TELEPORT_DEST)

    private fun both(from: WebWalkNode, to: WebWalkNode, type: EdgeType = EdgeType.WALK) =
        listOf(WebWalkEdge(from.id, to.id, type), WebWalkEdge(to.id, from.id, type))

    private val house = WebWalkGraph(
        listOf(west, doorWest, doorEast, east, far),
        both(west, doorWest) + both(doorWest, doorEast, EdgeType.DOOR) + both(doorEast, east),
        listOf(
            TeleportDefinition("varrock", TeleportKind.SPELL, "VARROCK", null, far.position, far.id, cost = 5),
            TeleportDefinition("home", TeleportKind.HOME, null, null, far.position, far.id, cost = 7),
        ),
        emptyList(),
    )

    /** Walks anywhere on one plane except across the wall between x 110 and 111, which only the door crosses. */
    private val walls = WalkEstimator { from, to ->
        if (from.z != to.z || (from.x <= 110) != (to.x <= 110)) null else ticksForTiles(from.computeLongestDistance(to))
    }

    private fun web(inWilderness: (Position) -> Boolean = { false }, estimator: (DoorCrossings) -> WalkEstimator = { walls }) =
        TravelWeb(house, inWilderness, estimator, Random(1))

    private fun legTypes(destination: Position, snapshot: CapabilitySnapshot = CapabilitySnapshot()): List<EdgeType>? =
        web().plan(start, listOf(destination), snapshot)?.legs?.map { it.type }

    @Test
    fun `a bank the web knows is used from its stand`() {
        val bank = BankDefinition("edgeville", booth = Position(120, 102), stand = Position(120, 101), links = listOf(BankLink(east.id, 1)))
        val web = TravelWeb(WebWalkGraph(listOf(east), emptyList(), emptyList(), emptyList(), listOf(bank)), { false }, { walls })
        assertEquals(Position(120, 101), web.bankStand(Position(120, 102)))
    }

    @Test
    fun `a booth the web does not know has no stand`() {
        assertNull(web().bankStand(Position(120, 102)))
    }

    @Test
    fun `a trip goes through the door of the web`() {
        assertEquals(listOf(EdgeType.WALK, EdgeType.DOOR, EdgeType.WALK), legTypes(pastTheDoor))
    }

    @Test
    fun `a trip never enters the wilderness`() {
        assertNull(web(inWilderness = { it.x >= 111 }).plan(start, listOf(pastTheDoor), CapabilitySnapshot()))
    }

    @Test
    fun `a trip ends at the nearest destination it can reach`() {
        val near = Position(95, 100)
        assertEquals(near, web().plan(start, listOf(pastTheDoor, near), CapabilitySnapshot())?.destination)
    }

    @Test
    fun `the walks are judged with the doors of the graph`() {
        var handed: DoorCrossings? = null
        val web = web(estimator = { doors -> handed = doors; walls })
        assertSame(web.doors, handed)
    }

    @Test
    fun `the reach scans get the doors of the graph with their objects`() {
        val opens = EdgeAction(objectId = 1530, objectPosition = doorEast.position)
        val graph = WebWalkGraph(listOf(doorWest, doorEast), listOf(WebWalkEdge(doorWest.id, doorEast.id, EdgeType.DOOR, action = opens)), emptyList(), emptyList())
        val web = TravelWeb(graph, { false }, { walls })
        assertEquals(Doorway(1530, doorEast.position, EdgeType.DOOR), web.doorways.between(doorWest.position, doorEast.position))
    }

    @Test
    fun `a trip teleports when the player may`() {
        val snapshot = CapabilitySnapshot(usableTeleports = setOf("varrock"))
        assertEquals(listOf(EdgeType.TELEPORT, EdgeType.WALK), legTypes(Position(402, 400), snapshot))
    }

    @Test
    fun `a trip never takes the home teleport`() {
        assertNull(legTypes(Position(402, 400), CapabilitySnapshot(usableTeleports = setOf("home"))))
    }
}
