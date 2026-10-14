package game.idle.location

import io.luna.game.model.Position
import kotlin.math.abs

/** A map tile. Kept separate from Luna's `Position` so data files and saves have no dependency on engine classes. */
data class Tile(val x: Int, val y: Int, val z: Int = 0) {

    fun toPosition(): Position = Position(x, y, z)

    /** The tile as flow lines write it: `x y`, with the floor only when it is not the ground floor. */
    fun text(): String = if (z == 0) "$x $y" else "$x $y $z"

    companion object {
        fun of(position: Position): Tile = Tile(position.x, position.y, position.z)
    }
}

/** A square of tiles around [anchor], [radius] tiles to each side. */
data class Area(val anchor: Tile, val radius: Int) {

    operator fun contains(tile: Tile): Boolean =
        tile.z == anchor.z && abs(tile.x - anchor.x) <= radius && abs(tile.y - anchor.y) <= radius

    /** Whether any tile lies in both this area and [other]. */
    fun overlaps(other: Area): Boolean {
        val reach = radius + other.radius
        return other.anchor.z == anchor.z && abs(other.anchor.x - anchor.x) <= reach && abs(other.anchor.y - anchor.y) <= reach
    }

    companion object {
        const val MAX_RADIUS = 32
    }
}
