package game.idle.autopilot

import game.idle.flow.Aim
import game.idle.location.Tile
import game.idle.movement.Reach
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject

/**
 * An object a processing step works at (a fire, a range, a furnace, an anvil) the player can walk to: [distance] walking
 * steps to [approach].
 */
data class PlaceCandidate(
    val objectId: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
) {
    val aim: Aim get() = Aim(objectId, Tile.of(position))

    companion object {
        /** [place] as the reach scan found it. */
        fun of(place: GameObject, reach: Reach) =
            PlaceCandidate(place.id, place.position, reach.distance, reach.usableFromHere, reach.approach)
    }
}
