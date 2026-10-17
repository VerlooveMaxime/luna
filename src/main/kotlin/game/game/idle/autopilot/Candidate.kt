package game.idle.autopilot

import api.bot.webwalk.model.EdgeType
import game.idle.flow.Aim
import game.idle.flow.Passage
import game.idle.flow.StepOutcome
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.DoorStep
import io.luna.game.model.Position

/**
 * A target a step could work at, as its look found it: a tree, a rock, a fire, a furnace or an anvil (its object
 * [id]), a fishing spot or an npc (its npc index), an item lying on the ground (its item id), standing on [position].
 * [distance] walking steps lead to [approach], the tile it is used from; none and the player's own tile when
 * [usableFromHere]. [attacking] when the npc fights the player; [fits] whether the bag has room for the item. [door] is
 * the door the walk there goes through: the shut one it opens first, else the first.
 */
data class Candidate(
    val id: Int,
    val position: Position,
    val distance: Int,
    val usableFromHere: Boolean,
    val approach: Position,
    val attacking: Boolean = false,
    val fits: Boolean = true,
    val door: DoorStep? = null,
) {
    /** What the retry rule remembers a decision by: the target's id on its tile. */
    val aim: Aim get() = Aim(id, Tile.of(position))

    /**
     * A walk up to it as the step reports it, with words for the door on the way as a trip's (Maxime, 2026-10-17):
     * "Opening the door" for a shut one, "Going through the door" for an open one, until the next decision.
     */
    val approached: StepOutcome
        get() {
            val door = door ?: return StepOutcome.Approached
            val passage = when (door.doorway.type) {
                EdgeType.GATE -> Passage.GATE
                EdgeType.CURTAIN -> Passage.CURTAIN
                else -> Passage.DOOR
            }
            return StepOutcome.OnTheWay(if (door.shut) UnderWay.Opening(passage) else UnderWay.Passing(passage))
        }

    companion object {

        /** One usable from here first, then the nearest by walking, ties west to east then south to north, so the choice is stable. */
        val NEAREST_FIRST: Comparator<Candidate> =
            compareByDescending<Candidate> { it.usableFromHere }
                .thenBy { it.distance }
                .thenBy { it.position.x }
                .thenBy { it.position.y }
    }
}
