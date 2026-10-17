package game.idle.autopilot

import api.bot.webwalk.model.EdgeType
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType

/**
 * A wall along x 3205 from y 3190 to 3210 on TestWorld's open map, the whole height of a step's area around 3200 3200,
 * with a shut door on 3205 3200 its one way through: the web's doorway there, opened from [inFront].
 */
object WalledDoor {

    private const val WALL = 1902
    private const val DOOR = 1530

    val inFront = Position(3204, 3200)
    val doorway = Doorway(DOOR, Position(3205, 3200), EdgeType.DOOR)

    /** The step a walk from the west takes through the shut door. */
    val shut = DoorStep(doorway, inFront, shut = true)

    /** Builds the wall and its door, and gives the doorways a scan needs to know it. */
    fun build(): Doorways {
        (3190..3210).filter { it != doorway.position.y }.forEach { y ->
            TestWorld.place(WALL, Position(3205, y), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        }
        TestWorld.place(DOOR, doorway.position, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        return Doorways(mapOf((inFront to doorway.position) to doorway))
    }
}
