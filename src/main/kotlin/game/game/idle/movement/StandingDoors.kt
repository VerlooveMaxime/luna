package game.idle.movement

import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * Whether a door, gate or curtain of the travel web still stands in [world]: shut on its own tile, or open within
 * [OPEN_LEAF_REACH] tiles of it, the open id [openOf] names. The web lists the doors of the cache's map, so a door
 * Luna removed at launch stands nowhere (Maxime, 2026-10-20).
 */
class StandingDoors(private val world: World, private val openOf: (Int) -> Int?) {

    fun stands(id: Int, at: Position): Boolean {
        if (has(id, at)) return true
        val open = openOf(id) ?: return false
        return (-OPEN_LEAF_REACH..OPEN_LEAF_REACH).any { dx ->
            (-OPEN_LEAF_REACH..OPEN_LEAF_REACH).any { dy -> has(open, at.translate(dx, dy)) }
        }
    }

    private fun has(id: Int, at: Position): Boolean = world.objects.findAll(at).anyMatch { it.id == id }

    private companion object {
        // A gate's far leaf opens two tiles from where it stands shut; every other leaf one tile or none.
        const val OPEN_LEAF_REACH = 2
    }
}
