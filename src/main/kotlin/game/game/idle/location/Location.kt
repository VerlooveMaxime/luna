package game.idle.location

import io.luna.game.model.Position

/** A map tile. Kept separate from Luna's `Position` so the data file has no dependency on engine classes. */
data class Tile(val x: Int, val y: Int, val z: Int = 0) {
    fun toPosition(): Position = Position(x, y, z)
}

data class LocationUnlock(val stage: Int)

/** Where one kind of resource grows within a location: the tile the search is centred on and how far it reaches. */
data class Area(val anchor: Tile, val radius: Int)

/**
 * A place a flow step can run at, from `data/idle/locations.jsonc`: a named part of the map with an optional bank
 * and one [Area] per kind of tree that grows there (later rocks and fishing spots). Resource names are raw here and
 * resolved per skill ([game.idle.autopilot.woodcutting.WoodcuttingSpot] for [trees]). [bank] is the tile of a bank
 * booth the flow's bank steps use from here, if the location has one.
 */
data class Location(
    val id: String,
    val name: String,
    val trees: Map<String, Area>,
    val bank: Tile?,
    val unlock: LocationUnlock,
)
