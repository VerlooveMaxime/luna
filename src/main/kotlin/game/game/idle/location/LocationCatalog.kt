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

internal data class LocationJson(
    val id: String = "",
    val name: String = "",
    val anchor: TileJson? = null,
    val radius: Int = 0,
    val trees: List<String> = emptyList(),
    val unlock: UnlockJson = UnlockJson(),
) {

    fun toLocation(): Location {
        require(id.isNotBlank()) { "A location has no id: $this" }
        require(name.isNotBlank()) { "Location '$id' has no name" }
        requireNotNull(anchor) { "Location '$id' has no anchor" }
        require(anchor.x >= 0) { "Location '$id' has a negative anchor x" }
        require(anchor.y >= 0) { "Location '$id' has a negative anchor y" }
        require(anchor.z in 0..3) { "Location '$id' has floor ${anchor.z}, expected 0 to 3" }
        require(radius in 1..LocationCatalog.MAX_RADIUS) {
            "Location '$id' has radius $radius, expected 1 to ${LocationCatalog.MAX_RADIUS}"
        }
        require(trees.isNotEmpty()) { "Location '$id' has nothing to do: no trees" }
        require(unlock.stage >= 0) { "Location '$id' unlocks at stage ${unlock.stage}, expected 0 or more" }
        return Location(
            id = id,
            name = name,
            anchor = Tile(anchor.x, anchor.y, anchor.z),
            radius = radius,
            trees = trees,
            unlock = LocationUnlock(unlock.stage),
        )
    }
}
