package game.idle.autopilot

import game.idle.flow.Aim
import game.idle.location.Tile
import game.idle.movement.Crossing
import io.luna.game.model.Position

/**
 * A target a step could work at, as its look found it: a tree, a rock, a fire, a furnace or an anvil (its object
 * [id]), a fishing spot or an npc (its npc index), an item lying on the ground (its item id), standing on [position].
 * [distance] walking steps lead to [approach], the tile it is used from; none and the player's own tile when
 * [usableFromHere]. [attacking] when the npc fights the player; [fits] whether the bag has room for the item. [crossings]
 * are the doors and entrances the walk there goes through, in that order.
 */
data class Candidate(
    val id: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
    val attacking: Boolean = false,
    val fits: Boolean = true,
    val crossings: List<Crossing> = emptyList(),
) {
    /** What the retry rule remembers a decision by: the target's id on its tile. */
    val aim: Aim get() = Aim(id, Tile.of(position))

    companion object {

        /** One usable from here first, then the nearest by walking, ties west to east then south to north, so the choice is stable. */
        val NEAREST_FIRST: Comparator<Candidate> =
            compareByDescending<Candidate> { it.usableFromHere }
                .thenBy { it.distance }
                .thenBy { it.position.x }
                .thenBy { it.position.y }
    }
}
