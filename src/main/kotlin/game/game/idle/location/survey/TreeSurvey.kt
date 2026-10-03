package game.idle.location.survey

import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import kotlin.math.roundToInt

/**
 * A place worth chopping [tree] at: [trees] counts every kind standing within [radius] of [anchor], [bank] is the
 * nearest booth on the floor in straight-line tiles (walking is at least as far).
 */
data class SpotCandidate(
    val tree: Tree,
    val anchor: Tile,
    val radius: Int,
    val trees: Map<Tree, Int>,
    val bank: BoothDistance?,
) {
    val count: Int get() = trees.getValue(tree)
}

/**
 * Finds where trees of one kind stand close together. The untaken tree with the most untaken trees of its kind within
 * [window] tiles seeds a candidate and those trees are taken, so each tree belongs to one candidate at most and the
 * densest spots come first. Stops once no tree has [minTrees] around it.
 */
class TreeSurvey(
    private val map: SurveyMap,
    private val window: Int = DEFAULT_WINDOW,
    private val minTrees: Int = MIN_TREES,
) {

    fun candidates(tree: Tree): List<SpotCandidate> {
        val pool = Pool(map.trees.filter { it.tree == tree }.map { it.tile }, window)
        return generateSequence { pool.takeDensest(minTrees) }.map { candidate(tree, it) }.toList()
    }

    private fun candidate(tree: Tree, group: List<Tile>): SpotCandidate {
        val anchor = Tile(
            x = group.map { it.x }.average().roundToInt(),
            y = group.map { it.y }.average().roundToInt(),
            z = group.first().z,
        )
        val radius = group.map { tilesApart(anchor, it) }.max().coerceAtLeast(1)
        val trees = map.treesWithin(anchor, radius).groupingBy { it.tree }.eachCount()
        return SpotCandidate(tree, anchor, radius, trees, map.nearestBooth(anchor))
    }

    private class Pool(private val tiles: List<Tile>, window: Int) {

        private val near: List<List<Int>> =
            tiles.map { tile -> tiles.indices.filter { tilesApart(tile, tiles[it]) <= window } }
        private val taken = BooleanArray(tiles.size)
        private val untakenNear = IntArray(tiles.size) { near[it].size }

        /** Null once every untaken tree has fewer than [minTrees] untaken trees near it, itself included. */
        fun takeDensest(minTrees: Int): List<Tile>? {
            val seed = tiles.indices.filter { !taken[it] }.maxByOrNull { untakenNear[it] }
            if (seed == null || untakenNear[seed] < minTrees) {
                return null
            }
            val group = near[seed].filter { !taken[it] }
            for (index in group) {
                taken[index] = true
                near[index].forEach { untakenNear[it]-- }
            }
            return group.map { tiles[it] }
        }
    }

    companion object {
        /** A candidate's trees then lie within twice this of its anchor, a radius a chop step accepts. */
        const val DEFAULT_WINDOW = Area.MAX_RADIUS / 2

        /** One tree is not a spot. */
        const val MIN_TREES = 2
    }
}
