package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import io.luna.game.model.Position

/** Object id of the trees west of the Varrock west bank; the planner never looks at it. */
const val TREE_OBJECT_ID = 1278

fun tree(
    x: Int,
    y: Int,
    distance: Int,
    usableFromHere: Boolean = false,
    approach: Position = Position(x - 1, y, 0),
): Candidate = Candidate(TREE_OBJECT_ID, Position(x, y, 0), distance, usableFromHere, approach)

fun view(trees: List<Candidate>, atLocation: Boolean = true): WoodcuttingView = WoodcuttingView(atLocation, trees)
