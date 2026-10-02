package game.idle.location

import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Every location the game knows, by id. Loaded once at boot from [PATH]; a bad file fails the boot. */
class LocationCatalog(locations: List<Location>) {

    private val byId: Map<String, Location> = locations.associateBy { it.id }

    val locations: List<Location> = locations

    val size: Int get() = byId.size

    init {
        val duplicates = locations.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate location ids: ${duplicates.sorted()}" }
    }

    fun find(id: String): Location? = byId[id]

    companion object {
        val PATH: Path = Paths.get("data", "idle", "locations.jsonc")
        const val MAX_RADIUS = 32

        fun parse(jsonc: String): LocationCatalog {
            val file = GsonUtils.GSON.fromJson(jsonc, LocationsJson::class.java) ?: LocationsJson()
            return LocationCatalog(file.locations.map { it.toLocation() })
        }

        fun load(path: Path): LocationCatalog = parse(Files.readString(path))
    }
}

/*
 * Raw Gson shapes. Every field has a default so Gson uses the no-arg constructor and a missing key can never leave a
 * Kotlin non-null field null; the conversion below turns each gap into a message that names the location.
 */

internal data class LocationsJson(val locations: List<LocationJson> = emptyList())

internal data class TileJson(val x: Int = -1, val y: Int = -1, val z: Int = 0)

internal data class UnlockJson(val stage: Int = 0)

internal data class AreaJson(val anchor: TileJson? = null, val radius: Int = 0)

internal data class LocationJson(
    val id: String = "",
    val name: String = "",
    val trees: Map<String, AreaJson> = emptyMap(),
    val bank: TileJson? = null,
    val unlock: UnlockJson = UnlockJson(),
) {

    fun toLocation(): Location {
        require(id.isNotBlank()) { "A location has no id: $this" }
        require(name.isNotBlank()) { "Location '$id' has no name" }
        require(trees.isNotEmpty()) { "Location '$id' has nothing to do: no trees" }
        require(unlock.stage >= 0) { "Location '$id' unlocks at stage ${unlock.stage}, expected 0 or more" }
        return Location(
            id = id,
            name = name,
            trees = trees.mapValues { (kind, area) -> area(kind, area) },
            bank = bank?.let { tile("bank", it) },
            unlock = LocationUnlock(unlock.stage),
        )
    }

    private fun area(kind: String, json: AreaJson): Area {
        val anchor = requireNotNull(json.anchor) { "Location '$id' has no anchor for its $kind trees" }
        require(json.radius in 1..LocationCatalog.MAX_RADIUS) {
            "Location '$id' has radius ${json.radius} for its $kind trees, expected 1 to ${LocationCatalog.MAX_RADIUS}"
        }
        return Area(tile("$kind anchor", anchor), json.radius)
    }

    private fun tile(what: String, json: TileJson): Tile {
        require(json.x >= 0) { "Location '$id' has a negative $what x" }
        require(json.y >= 0) { "Location '$id' has a negative $what y" }
        require(json.z in 0..3) { "Location '$id' has $what floor ${json.z}, expected 0 to 3" }
        return Tile(json.x, json.y, json.z)
    }
}
