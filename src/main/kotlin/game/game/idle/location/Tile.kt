package game.idle.location

import io.luna.game.model.Position

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

    companion object {
        const val MAX_RADIUS = 32
    }
}
