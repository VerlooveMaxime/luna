package game.idle.location

import io.luna.game.model.Position

/** A map tile. Kept separate from Luna's `Position` so the data file has no dependency on engine classes. */
data class Tile(val x: Int, val y: Int, val z: Int = 0) {
    fun toPosition(): Position = Position(x, y, z)
}

data class LocationUnlock(val stage: Int)

/**
 * A place a flow step can run at, from `data/idle/locations.jsonc`: where to stand and what is there. Resource names
 * are raw here and resolved per skill ([game.idle.autopilot.woodcutting.WoodcuttingSpot] for [trees]).
 */
data class Location(
    val id: String,
    val name: String,
    val anchor: Tile,
    val radius: Int,
    val trees: List<String>,
    val unlock: LocationUnlock,
)
