package game.idle.autopilot.woodcutting

import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position

/** Object id of the trees west of the Varrock west bank; the planner never looks at it. */
const val TREE_OBJECT_ID = 1278

fun tree(
    x: Int,
    y: Int,
    distance: Int,
    usableFromHere: Boolean = false,
    kind: Tree = Tree.NORMAL,
): TreeCandidate = TreeCandidate(TREE_OBJECT_ID, Position(x, y, 0), kind, distance, usableFromHere)

fun view(
    trees: List<TreeCandidate>,
    woodcuttingLevel: Int = 1,
    hasUsableAxe: Boolean = true,
    inventoryFull: Boolean = false,
    logsInInventory: Int = 0,
): WoodcuttingView = WoodcuttingView(woodcuttingLevel, hasUsableAxe, inventoryFull, logsInInventory, trees)
