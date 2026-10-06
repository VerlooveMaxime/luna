package game.idle.autopilot

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
    companion object {
        /** [place] as the reach scan found it. */
        fun of(place: GameObject, reach: Reach) =
            PlaceCandidate(place.id, place.position, reach.distance, reach.usableFromHere, reach.approach)
    }
}
