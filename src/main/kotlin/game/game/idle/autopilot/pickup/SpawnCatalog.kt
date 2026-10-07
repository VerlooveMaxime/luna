package game.idle.autopilot.pickup

import engine.spawn.ItemSpawn
import engine.spawn.ItemSpawnFileParser
import game.idle.location.Area
import game.idle.location.Tile

/**
 * Where items spawn, from Luna's item spawn file read through Luna's own parser, so the data and its format stay in
 * one place. Read from the data, not from what lies in the world, so a list does not change when someone picks an
 * item up (Maxime, 2026-10-07).
 */
class SpawnCatalog(private val spawns: List<ItemSpawn>) {

    /** The ids of the items that spawn in [area]. */
    fun itemsIn(area: Area): Set<Int> = spawns.filter { Tile.of(it.position) in area }.map { it.id }.toSet()

    companion object {
        /** Luna's spawn file; item names in it need the cache, which a booted server has loaded before its plugins. */
        fun load(): SpawnCatalog = SpawnCatalog(ItemSpawnFileParser.readSpawns())
    }
}
