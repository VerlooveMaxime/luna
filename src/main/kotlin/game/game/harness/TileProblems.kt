package game.harness

import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectGroup
import io.luna.game.model.`object`.ObjectType

/** An object covering a checked tile: its cache name, null for none, and how it is placed. */
data class TileObject(val name: String?, val type: ObjectType)

/**
 * Why a player put on a tile would stand wrong (Maxime, 2026-10-20): on ground no neighbour steps onto, or drawn inside
 * a wall or an object. Ground decorations (rugs, flowers, paths) lie under the player and fail nothing.
 */
object TileProblems {

    fun standing(standable: Boolean, objects: List<TileObject>): List<String> =
        listOfNotNull(NOT_STANDABLE.takeUnless { standable }) + objects.mapNotNull(::on)

    /** No trip from [from] that the flow's Travel tab allows gets there: a walk step's "No way there from here". */
    fun unreachable(from: Position): String = "no trip from ${from.x}, ${from.y} gets there with this flow's Travel settings"

    private fun on(obj: TileObject): String? {
        // Luna files a diagonal wall with the interactable objects, but it is drawn and blocks as a wall.
        val group = if (obj.type == ObjectType.DIAGONAL_WALL) ObjectGroup.WALL else obj.type.group
        val named = obj.name?.let { ": $it" }.orEmpty()
        return when (group) {
            ObjectGroup.WALL -> "a wall stands on it$named (${obj.type.name.lowercase().replace('_', ' ')})"
            ObjectGroup.WALL_DECORATION -> "a wall decoration hangs on it$named"
            ObjectGroup.INTERACTABLE -> "an object stands on it$named"
            ObjectGroup.GROUND_DECORATION -> null
        }
    }

    private const val NOT_STANDABLE = "a player cannot stand on it"
}
