package game.idle.autopilot.woodcutting

import api.predef.woodcutting
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import game.idle.autopilot.LunaClicks
import game.idle.movement.ReachTerrain
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Woodcutter] for a logged-in player at one [spot]. Trees are searched around the spot's anchor, not around the
 * player, and ranked by walking distance. Chopping and dropping go through the same events and interaction
 * action as the client's clicks, so Luna's reach checks, animations, XP and drop rules apply unchanged.
 */
class LunaWoodcutter(private val player: Player, private val spot: WoodcuttingSpot) : Woodcutter, ReachTerrain<StandingTree> {

    private val world get() = player.world
    private val collision: CollisionManager get() = world.collisionManager
    private val anchor: Position = spot.area.anchor.toPosition()
    private val scan = TreeScan(anchor, spot.area.radius)

    override fun isBusy(): Boolean = LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)

    override fun look(): WoodcuttingView =
        WoodcuttingView(
            woodcuttingLevel = player.woodcutting.level,
            hasUsableAxe = Axe.computeAxeType(player) != null,
            inventoryFull = player.inventory.isFull,
            logsInInventory = LOG_IDS.sumOf { player.inventory.computeAmountForId(it) },
            atLocation = scan.atLocation(player.position),
            trees = scan.candidates(player.position, standingTrees(), terrain = this),
        )

    override fun chop(tree: TreeCandidate) {
        val target = find(tree) ?: return
        LunaClicks.interact(player, ObjectFirstClickEvent(player, target), target, ObjectFirstClickEvent::class.java)
    }

    override fun walkTo(tree: TreeCandidate) = walkTo(tree.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        closeWindowsLikeAClick()
        player.navigator.navigate(tile, true)
    }

    override fun logs(): Int = player.inventory.computeAmountForId(spot.tree.logId)

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    /** What the walk packet sent before every world click does, without interrupting the current action. */
    private fun closeWindowsLikeAClick() {
        player.overlays.closeWindows(false)
    }

    private fun standingTrees(): List<StandingTree> =
        world.locator.findObjects(anchor, spot.area.radius, ::isStandingTree).map {
            StandingTree(it.id, it.position, maxOf(it.sizeX(), it.sizeY()), TreeStump.TREE_ID_MAP.getValue(it.id).tree)
        }

    // Objects found through their chunk are always ACTIVE: an object joins its chunk when it goes active and leaves
    // it when it goes inactive, so no state check is needed here or in find.
    private fun isStandingTree(obj: GameObject): Boolean = obj.id in spot.treeObjectIds && obj.isVisibleTo(player)

    private fun find(tree: TreeCandidate): GameObject? =
        world.objects.findAll(tree.position)
            .filter { it.id == tree.objectId && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    override fun canStep(from: Position, direction: Direction): Boolean =
        collision.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: StandingTree): Boolean =
        collision.reached(tile, target.position, STANDARD_SIZE)

    private companion object {
        val LOG_IDS: Set<Int> = Tree.ALL.keys
    }
}
