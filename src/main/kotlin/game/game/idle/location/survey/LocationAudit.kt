package game.idle.location.survey

import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.location.Location

/** Checks a [Location] against the map: every tree kind it names grows there, and its bank tile holds a booth. */
class LocationAudit(private val map: SurveyMap, private val minTrees: Int = MIN_TREES) {

    fun problems(location: Location): List<String> {
        val standing = map.treesWithin(location.anchor, location.radius).groupingBy { it.tree }.eachCount()
        val treeProblems = WoodcuttingSpot.from(location).trees.mapNotNull { tree ->
            val count = standing[tree] ?: 0
            if (count >= minTrees) {
                null
            } else {
                "Location '${location.id}' has $count ${tree.name.lowercase()} trees within ${location.radius} " +
                    "tiles of its anchor, expected at least $minTrees"
            }
        }
        val bankProblem = location.bank?.takeUnless(map::hasBooth)?.let {
            "Location '${location.id}' has no bank booth at ${it.x},${it.y},${it.z}"
        }
        return treeProblems + listOfNotNull(bankProblem)
    }

    companion object {
        /** One tree is not a spot. Low while one character works a spot; raise it when players share them. */
        const val MIN_TREES = 2
    }
}
