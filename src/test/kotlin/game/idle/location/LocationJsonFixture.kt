package game.idle.location

import io.luna.util.GsonUtils

/** JSON for one tree area: the search centre and radius, as the data file writes it. */
fun area(x: Int, y: Int, radius: Int, z: Int = 0): Map<String, Any> =
    mapOf("anchor" to mapOf("x" to x, "y" to y, "z" to z), "radius" to radius)

/** JSON for the `trees` table: every [kinds] entry at the same area, the tracked Varrock normal trees by default. */
fun trees(vararg kinds: String, x: Int = 3165, y: Int = 3445, radius: Int = 15): Map<String, Any> =
    kinds.associateWith { area(x, y, radius) }

/** JSON for one location: the tracked Varrock spot with [overrides] applied (`null` removes a key). */
fun locationJson(vararg overrides: Pair<String, Any?>): String {
    val fields = linkedMapOf<String, Any?>(
        "id" to "varrock_west",
        "name" to "West of Varrock",
        "trees" to trees("normal"),
        "unlock" to mapOf("stage" to 0),
    )
    for ((key, value) in overrides) {
        if (value == null) fields.remove(key) else fields[key] = value
    }
    return GsonUtils.GSON.toJson(fields)
}

fun catalogJson(vararg locations: String): String = """{ "locations": [${locations.joinToString(",")}] }"""

fun parseOne(vararg overrides: Pair<String, Any?>): Location =
    LocationCatalog.parse(catalogJson(locationJson(*overrides))).locations.single()
