package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position

/** Where a flight runs to: [tile], through [doors] in the order the walk takes them. */
data class FlightRoute(val tile: Position, val doors: List<DoorStep>)

/** Where a player runs to from npcs that attack them, and whether those npcs can still get at them. */
object FleePlanner {

    /** How far a flight goes before the next look: a few ticks of running. */
    const val MAX_STEPS = 10

    /** How far from the player an attacker is looked for, and how far its walk to them is followed. */
    const val THREAT_RANGE = 16

    /**
     * Where the player runs from [threats] within [MAX_STEPS] tiles of [here], walking by [step] (doors included). First
     * the nearest tile behind a door that [shutOut] says keeps every threat out once shut, among the tiles away from every
     * threat (web-walker movement Part 5, Maxime 2026-10-18: a barrier beats distance). Failing that, the tile farthest
     * from the nearest threat as the crow flies: first among the tiles away from every threat, so a run in the open never
     * passes one; cornered that way, among every tile, the run going past a threat (npcs do not block tiles), as a player
     * kept fleeing would (Maxime, 2026-10-10). Fewer steps first on a tie, a shut door counting its opening, then west to
     * east and south to north; null when no tile gets farther from them than [here].
     */
    fun pick(here: Position, threats: List<Position>, step: (Position, Direction) -> Step?, shutOut: ShutOut): FlightRoute? {
        if (threats.isEmpty()) return null
        val distances = WalkingDistances.through(here, TileBounds.around(here, MAX_STEPS), step)
        val nearest = compareBy<Position> { distances[it] }.thenBy { it.x }.thenBy { it.y }
        // The player's own tile is always reachable and away from every threat, so both sets have a best tile.
        val away = distances.reachable.filter { tile -> threats.all { away(here, tile, it) } }
        val behindDoor = away.filter { tile -> distances.doorsOn(tile).any { shutOut.keepsOut(it.doorway, tile) } }.minWithOrNull(nearest)
        val farthest = compareByDescending<Position> { safety(it, threats) }.then(nearest)
        val tile = behindDoor ?: listOf(away.minWith(farthest), distances.reachable.minWith(farthest)).firstOrNull { safety(it, threats) > safety(here, threats) }
        return tile?.let { FlightRoute(it, distances.doorsOn(it)) }
    }

    /**
     * Whether an attacker standing on [from] can walk by [canStep] up beside [target] with nothing between them that
     * [onto] (a step onto [target]) stops, following its walk no farther than [THREAT_RANGE] from [target]: a fence, a
     * closed gate or a shut door in between keeps it out, even right behind it.
     */
    fun canGetAt(from: Position, target: Position, canStep: (Position, Direction) -> Boolean, onto: (Position, Direction) -> Boolean): Boolean {
        if (from.z != target.z || from.computeLongestDistance(target) > THREAT_RANGE) return false
        val reached = WalkingDistances.from(from, TileBounds.around(target, THREAT_RANGE), canStep).reachable
        return gotAt(target, reached, onto)
    }

    /** Squared distance to the nearest threat. */
    private fun safety(tile: Position, threats: List<Position>): Int = threats.map { squaredDistance(tile, it) }.min()

    /** Whether going from [here] to [tile] does not head towards [threat]. */
    private fun away(here: Position, tile: Position, threat: Position): Boolean =
        (tile.x - here.x) * (here.x - threat.x) + (tile.y - here.y) * (here.y - threat.y) >= 0

    private fun squaredDistance(a: Position, b: Position): Int = (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
}

/**
 * Whether a walk that [reached] these tiles gets at [target]: onto it, or beside it with a step [onto] it open. A
 * player's own tile is closed to an npc's walk (npcs go round players), so the walk itself never steps onto it.
 */
private fun gotAt(target: Position, reached: Set<Position>, onto: (Position, Direction) -> Boolean): Boolean =
    target in reached || Direction.ALL_EXCEPT_NONE.any { direction ->
        val beside = target.translate(1, direction)
        beside in reached && onto(beside, direction.opposite())
    }

/**
 * Whether shutting a door would keep every one of [threats] from getting at a tile near [here] (web-walker movement Part
 * 5), as [FleePlanner.canGetAt] has it: their walks by [npcStep], the last step [onto] the tile, with the door's leaves
 * shut ([Doorways.leavesOf]), as far as a flight from [here] and an attacker's walk reach. Npcs open no doors, so a shut
 * door is a wall to them.
 */
class ShutOut(
    here: Position,
    private val threats: List<Position>,
    private val npcStep: (Position, Direction) -> Boolean,
    private val onto: (Position, Direction) -> Boolean,
    private val doorways: Doorways,
) {

    private val bounds = TileBounds.around(here, FleePlanner.THREAT_RANGE + FleePlanner.MAX_STEPS)

    /** The tiles some threat walks onto with each door shut, worked out once a door. */
    private val reached = hashMapOf<Doorway, Set<Position>>()

    fun keepsOut(door: Doorway, tile: Position): Boolean {
        val leaves = doorways.leavesOf(door)
        val walked = reached.getOrPut(door) { reachedPast(leaves) }
        return !gotAt(tile, walked, shut(onto, leaves))
    }

    private fun reachedPast(leaves: Set<Doorway>): Set<Position> {
        val step = shut(npcStep, leaves)
        return threats.filter { it in bounds }.flatMapTo(hashSetOf()) { WalkingDistances.from(it, bounds, step).reachable }
    }

    /** [step] with [leaves] shut. */
    private fun shut(step: (Position, Direction) -> Boolean, leaves: Set<Doorway>): (Position, Direction) -> Boolean =
        { from, direction -> step(from, direction) && doorways.crossedBy(from, from.translate(1, direction)) !in leaves }
}
