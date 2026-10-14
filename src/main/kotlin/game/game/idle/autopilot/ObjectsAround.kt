package game.idle.autopilot

import game.idle.location.Area
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.idle.movement.footprintOf
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * The objects of [objectIds] standing around a step's [area] that the player can see, as the step looks at them: each
 * with the walk to a tile it is used from, the reach checked against the object itself as the click's listener
 * checks it (a tree is cut from a tile beside it, never a diagonal one; a long range from its open side). The walk is
 * [LunaClicks.canStep]'s.
 */
class ObjectsAround(private val player: Player, private val area: Area, private val objectIds: Set<Int>) : ReachTerrain<GameObject> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)

    /** Whether the player stands inside the area. */
    fun atLocation(): Boolean = scan.atLocation(player.position)

    /** Each object the player can walk up to, with how; none from too far off the area or another floor. */
    fun candidates(): List<Candidate> =
        scan.reachable(player.position, standing(), ::footprintOf, terrain = this)
            .map { (obj, reach) -> Candidate(obj.id, obj.position, reach.distance, reach.usableFromHere, reach.approach) }

    /**
     * The object [candidate] was, if it still stands there for the player: a tree cut down, a rock emptied or a fire
     * burnt out since the look is another object, or none.
     */
    fun find(candidate: Candidate): GameObject? =
        world.objects.findAll(candidate.position)
            .filter { it.id == candidate.id && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    // Objects found through their chunk are always ACTIVE, so only the id needs checking.
    private fun standing(): List<GameObject> =
        world.locator.findObjects(anchor, area.radius) { it.id in objectIds && it.isVisibleTo(player) }.toList()

    override fun canStep(from: Position, direction: Direction): Boolean = LunaClicks.canStep(world, from, direction)

    override fun reachedFrom(tile: Position, target: GameObject): Boolean = world.collisionManager.reached(tile, target, STANDARD_SIZE)
}
