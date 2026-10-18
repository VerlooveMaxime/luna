package game.idle.movement

import api.bot.webwalk.model.EdgeType
import io.luna.game.model.Direction
import io.luna.game.model.Position

/**
 * A walled room east of 3200 3200 on an open map, for flight tests: inside x 3203 to 3205 and y 3199 to 3201, walls of
 * whole tiles round it, the way in its [door] on 3202 3200 crossed from [outside], [shut] or open; with a [backWay],
 * a gap in the east wall too. Players and npcs walk alike here, through the door only while it is open.
 */
class FleeRoom(private val shut: Boolean = true, backWay: Boolean = false) {

    val door = Doorway(1530, Position(3202, 3200), EdgeType.DOOR)
    val outside = Position(3201, 3200)
    val doorways = Doorways(mapOf((outside to door.position) to door))

    /** A tile inside, behind the door. */
    val inside = Position(3204, 3200)

    private val walls: Set<Position> =
        (3202..3206).flatMap { x -> (3198..3202).map { y -> Position(x, y) } }
            .filter { it.x == 3202 || it.x == 3206 || it.y == 3198 || it.y == 3202 }
            .toSet() - door.position - (if (backWay) setOf(Position(3206, 3200)) else emptySet())

    private val steps = ReachSteps(::traversable, doorways, inWilderness = { false })

    fun step(from: Position, direction: Direction): Step? = steps.step(from, direction)

    fun npcStep(from: Position, direction: Direction): Boolean = traversable(from, direction)

    fun shutOut(here: Position, threats: List<Position>): ShutOut = ShutOut(here, threats, ::npcStep, ::npcStep, doorways)

    private fun traversable(from: Position, direction: Direction): Boolean {
        val to = from.translate(1, direction)
        return to !in walls && !(shut && doorways.crossedBy(from, to) != null)
    }
}
