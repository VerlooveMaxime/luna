package game.idle.location

import io.luna.util.GsonUtils

/** JSON for one location: the tracked Varrock spot with [overrides] applied (`null` removes a key). */
fun locationJson(vararg overrides: Pair<String, Any?>): String {
    val fields = linkedMapOf<String, Any?>(
        "id" to "varrock_west",
        "name" to "West of Varrock",
        "anchor" to mapOf("x" to 3165, "y" to 3445),
        "radius" to 15,
        "trees" to listOf("normal"),
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
