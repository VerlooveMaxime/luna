package game.idle.location.survey

import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.location.Location

/** Checks a [Location] against the map: every tree kind it names grows in its area, and its bank tile holds a booth. */
class LocationAudit(private val map: SurveyMap, private val minTrees: Int = MIN_TREES) {

    fun problems(location: Location): List<String> {
        val treeProblems = WoodcuttingSpot.from(location).values.mapNotNull { spot ->
            val kind = spot.tree.name.lowercase()
            val count = map.treesWithin(spot.area.anchor, spot.area.radius).count { it.tree == spot.tree }
            if (count >= minTrees) {
                null
            } else {
                "Location '${location.id}' has $count $kind trees within ${spot.area.radius} tiles of its $kind anchor, " +
                    "expected at least $minTrees"
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
