package game.idle.location.survey

import game.idle.location.Location
import game.idle.location.LocationUnlock
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree

/** A placement of [kind] at x, y; the object id is never read by the survey. */
fun placed(x: Int, y: Int, kind: Tree = Tree.NORMAL, z: Int = 0): TreePlacement = TreePlacement(kind, 1278, Tile(x, y, z))

fun surveyMap(vararg trees: TreePlacement, booths: List<Tile> = emptyList()): SurveyMap = SurveyMap(trees.toList(), booths)

fun location(
    anchor: Tile = Tile(100, 100),
    radius: Int = 5,
    trees: List<String> = listOf("normal"),
    bank: Tile? = null,
): Location = Location("spot", "Spot", anchor, radius, trees, bank, LocationUnlock(stage = 0))
