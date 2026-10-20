package game.idle.movement

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.FairyRing
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import api.bot.webwalk.walk.crossing.CrossingHandlers
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TravelGraphTest {

    /** A square of wilderness from 10, 10 to 20, 20. */
    private val wilderness: (Position) -> Boolean = { it.x in 10..20 && it.y in 10..20 }

    private val west = WebWalkNode("west", Position(0, 15), NodeKind.WAYPOINT)
    private val east = WebWalkNode("east", Position(30, 15), NodeKind.WAYPOINT)
    private val south = WebWalkNode("south", Position(30, 0), NodeKind.WAYPOINT)
    private val inside = WebWalkNode("inside", Position(15, 15), NodeKind.WAYPOINT)
    private val deeper = WebWalkNode("deeper", Position(12, 18), NodeKind.WAYPOINT)
    private val nodes = listOf(west, east, south, inside, deeper)

    private fun edge(from: WebWalkNode, to: WebWalkNode, type: EdgeType = EdgeType.WALK) = WebWalkEdge(from.id, to.id, type)

    private fun edgesKept(vararg edges: WebWalkEdge): List<WebWalkEdge> =
        travelGraph(WebWalkGraph(nodes, edges.toList(), emptyList(), emptyList()), wilderness).edges

    private fun teleport(id: String, kind: TeleportKind, to: WebWalkNode) =
        TeleportDefinition(id, kind, key = null, option = null, destination = to.position, nodeId = to.id, cost = 5)

    private fun teleportsKept(vararg teleports: TeleportDefinition): List<String> =
        travelGraph(WebWalkGraph(nodes, emptyList(), teleports.toList(), emptyList()), wilderness).teleports.map { it.id }

    private fun banksKept(vararg banks: BankDefinition): List<BankDefinition> =
        travelGraph(WebWalkGraph(nodes, emptyList(), emptyList(), emptyList(), banks.toList()), wilderness).banks

    @Test
    fun `a door from outside into the wilderness is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(edge(west, inside, EdgeType.DOOR)))
    }

    @Test
    fun `a door out of the wilderness is kept, so a player in it can leave`() {
        val out = edge(inside, west, EdgeType.DOOR)
        assertEquals(listOf(out), edgesKept(out))
    }

    @Test
    fun `a walk whose line crosses a corner of the wilderness is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(edge(west, east)))
    }

    @Test
    fun `a walk that stays outside the wilderness is kept`() {
        val walk = edge(east, south)
        assertEquals(listOf(walk), edgesKept(walk))
    }

    @Test
    fun `a walk inside the wilderness is kept, so a player in it can walk out`() {
        val walk = edge(inside, deeper)
        assertEquals(listOf(walk), edgesKept(walk))
    }

    @Test
    fun `a ladder between two tiles outside the wilderness is kept whatever lies between them`() {
        val ladder = edge(west, east, EdgeType.LADDER)
        assertEquals(listOf(ladder), edgesKept(ladder))
    }

    @Test
    fun `a cave entrance outside the wilderness is kept`() {
        val entrance = edge(east, south, EdgeType.ENTRANCE)
        assertEquals(listOf(entrance), edgesKept(entrance))
    }

    /** An entrance over the object of [id], in a graph where only the object [changing] changes form. */
    private fun entranceOver(id: Int, changing: Int = boulder): WebWalkEdge {
        val entrance = edge(east, south, EdgeType.ENTRANCE).copy(action = EdgeAction(objectId = id, objectPosition = south.position))
        return travelGraph(WebWalkGraph(nodes, listOf(entrance), emptyList(), emptyList()), wilderness) { it == changing }.edges.single()
    }

    private val boulder = 11045

    @Test
    fun `an edge over an object that changes form needs the player to see an option on it`() {
        assertEquals(setOf(shownFlag(boulder)), entranceOver(boulder).requirements.flags)
    }

    @Test
    fun `an edge over an object that keeps its form needs nothing more`() {
        assertEquals(Requirements.NONE, entranceOver(4499).requirements)
    }

    @Test
    fun `an edge over no object needs nothing more`() {
        val walk = edge(east, south)
        assertEquals(listOf(walk), travelGraph(WebWalkGraph(nodes, listOf(walk), emptyList(), emptyList()), wilderness) { true }.edges)
    }

    private val door = Doorway(1530, south.position, EdgeType.DOOR)

    private fun doorEdge(from: WebWalkNode, to: WebWalkNode) =
        edge(from, to, EdgeType.DOOR).copy(action = EdgeAction(objectId = door.id, objectPosition = door.position))

    private fun withRemoved(vararg edges: WebWalkEdge): List<WebWalkEdge> =
        travelGraph(WebWalkGraph(nodes, edges.toList(), emptyList(), emptyList()), wilderness, removed = setOf(door)).edges

    @Test
    fun `a removed door's edge is a walk through its empty doorway`() {
        assertEquals(listOf(edge(east, south)), withRemoved(doorEdge(east, south)))
    }

    @Test
    fun `a removed door from outside into the wilderness is still left out`() {
        assertEquals(emptyList<WebWalkEdge>(), withRemoved(doorEdge(west, inside)))
    }

    @Test
    fun `a door still standing keeps its edge`() {
        val other = edge(east, south, EdgeType.DOOR).copy(action = EdgeAction(objectId = 1533, objectPosition = door.position))
        assertEquals(listOf(other), withRemoved(other))
    }

    @Test
    fun `an entrance over the removed door's object and tile stays an entrance`() {
        val entrance = edge(east, south, EdgeType.ENTRANCE).copy(action = EdgeAction(objectId = door.id, objectPosition = door.position))
        assertEquals(listOf(entrance), withRemoved(entrance))
    }

    private fun removedOf(vararg edges: WebWalkEdge, stands: (Int, Position) -> Boolean): Set<Doorway> =
        removedDoors(WebWalkGraph(nodes, edges.toList(), emptyList(), emptyList()), stands)

    @Test
    fun `a door that stands nowhere is removed, once for both ways across`() {
        assertEquals(setOf(door), removedOf(doorEdge(east, south), doorEdge(south, east)) { _, _ -> false })
    }

    @Test
    fun `a door that stands is not removed`() {
        assertEquals(emptySet<Doorway>(), removedOf(doorEdge(east, south)) { id, at -> id == door.id && at == door.position })
    }

    @Test
    fun `only doors, gates and curtains are asked whether they stand`() {
        val entrance = edge(east, south, EdgeType.ENTRANCE).copy(action = EdgeAction(objectId = 4499, objectPosition = south.position))
        assertEquals(emptySet<Doorway>(), removedOf(entrance) { _, _ -> false })
    }

    @Test
    fun `a crossing is not asked whether it stands`() {
        assertEquals(emptySet<Doorway>(), removedOf(crossing(BorderGateCrossing.ID)) { _, _ -> false })
    }

    @Test
    fun `a door edge naming no object is not removed`() {
        assertEquals(emptySet<Doorway>(), removedOf(edge(east, south, EdgeType.GATE)) { _, _ -> false })
    }

    @Test
    fun `the nodes in the wilderness stay, so a trip can start from one`() {
        val graph = travelGraph(WebWalkGraph(nodes, emptyList(), emptyList(), emptyList()), wilderness)
        assertEquals(nodes.map { it.id }.toSet(), graph.nodes.keys)
    }

    @Test
    fun `a teleport landing in the wilderness is left out`() {
        assertEquals(listOf("varrock"), teleportsKept(teleport("varrock", TeleportKind.SPELL, east), teleport("annakarl", TeleportKind.SPELL, inside)))
    }

    @Test
    fun `the home teleport is left out`() {
        assertEquals(listOf("glory"), teleportsKept(teleport("home", TeleportKind.HOME, east), teleport("glory", TeleportKind.JEWELLERY, south)))
    }

    @Test
    fun `fairy rings are left out while no leg takes one`() {
        val rings = listOf(FairyRing("outside", "AIQ", east.position, east.id))
        val graph = travelGraph(WebWalkGraph(nodes, emptyList(), emptyList(), rings), wilderness)
        assertEquals(emptyList<FairyRing>(), graph.fairyRings)
    }

    @Test
    fun `an edge of a kind no leg takes is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(edge(east, south, EdgeType.FAIRY_RING)))
    }

    private fun crossing(handler: String?) =
        edge(east, south, EdgeType.CROSSING).copy(action = EdgeAction(objectId = 2882, objectPosition = south.position, handler = handler))

    @Test
    fun `the toll gate's crossing is kept`() {
        val toll = crossing(BorderGateCrossing.ID)
        assertEquals(listOf(toll), edgesKept(toll))
    }

    @Test
    fun `a crossing whose handler no leg takes is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(crossing("shantay_pass")))
    }

    @Test
    fun `a crossing naming no handler is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(crossing(null)))
    }

    @Test
    fun `a crossing with nothing to click is left out`() {
        assertEquals(emptyList<WebWalkEdge>(), edgesKept(edge(east, south, EdgeType.CROSSING)))
    }

    @Test
    fun `every kind of edge upstream's model has, a trip has a leg for, but fairy rings`() {
        assertEquals(
            setOf(EdgeType.FAIRY_RING), EdgeType.entries.toSet() - TRIP_EDGE_TYPES,
            "an upstream merge brought a new kind of edge: write its leg in TripWork and add it to TRIP_EDGE_TYPES",
        )
    }

    @Test
    fun `every crossing handler upstream has, a trip has a leg for`() {
        assertEquals(
            CrossingHandlers.ids, TRIP_CROSSING_HANDLERS,
            "an upstream merge brought a new crossing: write its leg in TripWork and add it to TRIP_CROSSING_HANDLERS",
        )
    }

    @Test
    fun `a bank standing in the wilderness is left out`() {
        val wild = BankDefinition("wild", booth = Position(14, 14), stand = Position(14, 13), links = listOf(BankLink(east.id, 10)))
        assertEquals(emptyList<BankDefinition>(), banksKept(wild))
    }

    @Test
    fun `a bank loses its walks from nodes in the wilderness`() {
        val bank = BankDefinition("edge", booth = Position(22, 15), stand = Position(22, 14), links = listOf(BankLink(inside.id, 4), BankLink(east.id, 5)))
        assertEquals(listOf(BankLink(east.id, 5)), banksKept(bank).single().links)
    }

    @Test
    fun `a bank walked to only from the wilderness is left out`() {
        val bank = BankDefinition("edge", booth = Position(22, 15), stand = Position(22, 14), links = listOf(BankLink(inside.id, 4)))
        assertEquals(emptyList<BankDefinition>(), banksKept(bank))
    }
}
