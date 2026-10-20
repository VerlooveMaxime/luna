package game.idle.movement

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import io.luna.game.model.Position

/**
 * Upstream's web-walker graph as the autopilot may travel it. Nothing enters the wilderness: no edge from outside it to
 * a node in it, no walk edge whose straight line steps into it (a walk along the border may cut a corner), no teleport
 * or fairy ring landing in it, no bank in it; a player already in it may still walk out. The home teleport is left out:
 * it is Luna's `::home` command, which 2006 had no spell for (Maxime, 2026-10-17). An edge over an object that
 * [changesForm] needs the player to see an option on it: its [shownFlag] (Maxime, 2026-10-20). The edge of a door that
 * is [removed] is a walk through its empty doorway (Maxime, 2026-10-20).
 */
fun travelGraph(
    graph: WebWalkGraph,
    inWilderness: (Position) -> Boolean,
    removed: Set<Doorway> = emptySet(),
    changesForm: (Int) -> Boolean = { false },
): WebWalkGraph {
    val wild = graph.nodes.values.filter { inWilderness(it.position) }.map { it.id }.toSet()
    val edges = graph.edges.map { edge -> walkedIfRemoved(edge, removed) }
        .filter { edge -> !enters(graph, edge, wild, inWilderness) }.map { edge -> seenOnly(edge, changesForm) }
    val teleports = graph.teleports.filter { it.kind != TeleportKind.HOME && it.nodeId !in wild }
    val rings = graph.fairyRings.filter { it.nodeId !in wild }
    val banks = graph.banks.filter { !inWilderness(it.stand) }
        .map { bank -> bank.copy(links = bank.links.filter { it.nodeId !in wild }) }
        .filter { it.links.isNotEmpty() }
    return WebWalkGraph(graph.nodes.values, edges, teleports, rings, banks)
}

/** The loader refuses an edge whose nodes are unknown, so both are there. */
private fun enters(graph: WebWalkGraph, edge: WebWalkEdge, wild: Set<String>, inWilderness: (Position) -> Boolean): Boolean {
    if (edge.from !in wild && edge.to in wild) return true
    if (edge.type != EdgeType.WALK) return false
    val from = graph.nodes.getValue(edge.from).position
    val to = graph.nodes.getValue(edge.to).position
    return entersWilderness(from, routeTiles(from, listOf(to)), inWilderness)
}

/** The doors, gates and curtains of [graph] that no longer [stand] where it has them. */
fun removedDoors(graph: WebWalkGraph, stands: (Int, Position) -> Boolean): Set<Doorway> =
    graph.edges.filter { it.type.isDoorLike }.mapNotNull(::doorwayOf).distinct().filterNot { stands(it.id, it.position) }.toSet()

// A walk keeps the door's nodes linked: the walk graph was linked with the door counted shut, so no other walk crosses there.
private fun walkedIfRemoved(edge: WebWalkEdge, removed: Set<Doorway>): WebWalkEdge =
    if (edge.type.isDoorLike && doorwayOf(edge) in removed) edge.copy(type = EdgeType.WALK, cost = null, action = null) else edge

/** The planner's flag of a player who sees an option on the object of [id]. */
fun shownFlag(id: Int): String = "option_shown:$id"

private fun seenOnly(edge: WebWalkEdge, changesForm: (Int) -> Boolean): WebWalkEdge {
    val id = edge.action?.objectId?.takeIf(changesForm) ?: return edge
    return edge.copy(requirements = edge.requirements.copy(flags = edge.requirements.flags + shownFlag(id)))
}
