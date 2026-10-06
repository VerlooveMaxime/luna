package io.luna.game.model.collision

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reaching a one-tile object open on every side (Tutorial Island's ladder down to the mine) from the tile beside it. */
class ObjectReachTest {

    private val ladder = 3029
    private val deadTree = 1286
    private val wall = 1902

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun reached(from: Position, objectAt: Position): Boolean =
        TestWorld.world.collisionManager.reached(from, TestWorld.place(ladder, objectAt), STANDARD_SIZE)

    private fun wallOn(tile: Position, side: ObjectDirection) {
        TestWorld.place(wall, tile, ObjectType.STRAIGHT_WALL, side)
    }

    @Test
    fun `an object on the top row of its chunk is reached from the tile above, in the next chunk`() {
        // The tile at the player's place within their chunk, but in the object's chunk: what the check used to read.
        TestWorld.place(deadTree, Position(3200, 3200))

        assertTrue(reached(from = Position(3200, 3208), objectAt = Position(3200, 3207)))
    }

    @Test
    fun `a wall on the south side of the player's tile stops the reach to an object south of it`() {
        val from = Position(3210, 3204)
        wallOn(from, ObjectDirection.SOUTH)

        assertFalse(reached(from, objectAt = Position(3210, 3203)))
    }

    @Test
    fun `a wall on the north side of the player's tile stops the reach to an object north of it`() {
        val from = Position(3220, 3203)
        wallOn(from, ObjectDirection.NORTH)

        assertFalse(reached(from, objectAt = Position(3220, 3204)))
    }

    @Test
    fun `a wall on the east side of the player's tile stops the reach to an object east of it`() {
        val from = Position(3230, 3203)
        wallOn(from, ObjectDirection.EAST)

        assertFalse(reached(from, objectAt = Position(3231, 3203)))
    }

    @Test
    fun `a wall on the west side of the player's tile stops the reach to an object west of it`() {
        val from = Position(3241, 3203)
        wallOn(from, ObjectDirection.WEST)

        assertFalse(reached(from, objectAt = Position(3240, 3203)))
    }

    @Test
    fun `a wall on another side of the player's tile leaves the reach alone`() {
        val from = Position(3250, 3204)
        wallOn(from, ObjectDirection.NORTH)

        assertTrue(reached(from, objectAt = Position(3250, 3203)))
    }
}
