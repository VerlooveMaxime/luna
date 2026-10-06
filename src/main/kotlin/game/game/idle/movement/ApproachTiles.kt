package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Tiles bordering a [width] by [height] footprint whose south-west tile is [corner], nearest to [from] first (ties
 * broken west to east, then south to north, so the choice is stable). Callers keep only the tiles the reach check
 * accepts.
 */
fun approachTiles(corner: Position, width: Int, height: Int, from: Position): List<Position> {
    require(width > 0 && height > 0) { "A footprint is at least one tile, got $width by $height" }
    val xs = corner.x - 1..corner.x + width
    val ys = corner.y - 1..corner.y + height
    val footprintXs = corner.x until corner.x + width
    val footprintYs = corner.y until corner.y + height
    return xs.flatMap { x -> ys.map { y -> Position(x, y, corner.z) } }
        .filterNot { it.x in footprintXs && it.y in footprintYs }
        .sortedWith(compareBy({ squaredDistance(it, from) }, { it.x }, { it.y }))
}

/**
 * The tiles [obj] covers: its definition's sides, swapped when it is turned a quarter (facing north or south), as the
 * client lays it out. A square of the longer side would swallow the tiles in front of a long object, such as Tutorial
 * Island's range, which stands in an alcove one tile deep.
 */
fun footprintOf(obj: GameObject): Footprint {
    val turned = obj.direction == ObjectDirection.NORTH || obj.direction == ObjectDirection.SOUTH
    return if (turned) Footprint(obj.position, obj.sizeY(), obj.sizeX()) else Footprint(obj.position, obj.sizeX(), obj.sizeY())
}

/**
 * Whether a player can stand on [tile]: some neighbour can step onto it. Luna's `isBlocked` is no answer, since a wall
 * on any one side of a tile counts as blocking all of it.
 */
fun canStandOn(collision: CollisionManager, tile: Position): Boolean =
    Direction.NESW.any { collision.traversable(tile.translate(1, it), EntityType.PLAYER, it.opposite()) }

private fun squaredDistance(a: Position, b: Position): Int {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
