package game.harness

import io.luna.game.model.Position

/**
 * Tiles bordering a [size] by [size] footprint whose south-west tile is [corner], nearest to [from] first (ties
 * broken west to east, then south to north, so the choice is stable).
 *
 * The square covers any rotation of the object; callers keep only the tiles the reach check accepts.
 */
fun approachTiles(corner: Position, size: Int, from: Position): List<Position> {
    require(size > 0) { "size must be positive, got $size" }
    val xs = corner.x - 1..corner.x + size
    val ys = corner.y - 1..corner.y + size
    val footprintXs = corner.x until corner.x + size
    val footprintYs = corner.y until corner.y + size
    return xs.flatMap { x -> ys.map { y -> Position(x, y, corner.z) } }
        .filterNot { it.x in footprintXs && it.y in footprintYs }
        .sortedWith(compareBy({ squaredDistance(it, from) }, { it.x }, { it.y }))
}

private fun squaredDistance(a: Position, b: Position): Int {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
}
