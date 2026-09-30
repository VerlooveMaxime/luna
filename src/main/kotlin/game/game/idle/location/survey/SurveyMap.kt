package game.idle.location.survey

import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.cache.map.MapObject
import kotlin.math.abs

/** A tree the cache places in the map. [tile] is its south-west corner, the tile the world locator measures from. */
data class TreePlacement(val tree: Tree, val objectId: Int, val tile: Tile)

data class BoothDistance(val booth: Tile, val distance: Int)

/**
 * Every tree and bank booth of the map as the cache places them, before any plugin runs. Distances are counted like
 * `WorldLocator.findObjects` counts them: tiles along the longer axis, on one floor.
 */
class SurveyMap(val trees: List<TreePlacement>, val booths: List<Tile>) {

    private val boothTiles: Set<Tile> = booths.toSet()

    fun treesWithin(anchor: Tile, radius: Int): List<TreePlacement> =
        trees.filter { tilesApart(anchor, it.tile) <= radius }

    fun hasBooth(tile: Tile): Boolean = tile in boothTiles

    /** Null when no booth stands on the anchor's floor. */
    fun nearestBooth(anchor: Tile): BoothDistance? =
        booths.filter { it.z == anchor.z }.map { BoothDistance(it, tilesApart(anchor, it)) }.minByOrNull { it.distance }

    companion object {

        /** Keeps the placements of [treeKinds] (alive tree object id to kind) and of [boothIds]. */
        fun from(objects: Iterable<MapObject>, treeKinds: Map<Int, Tree>, boothIds: Set<Int>): SurveyMap {
            val trees = objects.mapNotNull { placed ->
                treeKinds[placed.objectId]?.let { TreePlacement(it, placed.objectId, placed.tile()) }
            }
            val booths = objects.filter { it.objectId in boothIds }.map { it.tile() }
            return SurveyMap(trees, booths)
        }

        private fun MapObject.tile(): Tile = Tile(position.x, position.y, position.z)
    }
}

/** [Int.MAX_VALUE] for tiles on different floors, which never count as near. */
fun tilesApart(a: Tile, b: Tile): Int =
    if (a.z != b.z) Int.MAX_VALUE else maxOf(abs(a.x - b.x), abs(a.y - b.y))
