package game.idle.autopilot.woodcutting

import api.predef.woodcutting
import engine.widget.skill.LevelUpInterface
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionManager
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.`object`.GameObject

/**
 * [Woodcutter] for a logged-in player at one [spot]. Trees are searched around the location's anchor, not around
 * the player, and ranked by walking distance. Chopping and dropping go through the same events and interaction
 * action as the client's clicks, so Luna's reach checks, animations, XP and drop rules apply unchanged.
 */
class LunaWoodcutter(private val player: Player, private val spot: WoodcuttingSpot) : Woodcutter, Terrain {

    private val world get() = player.world
    private val collision: CollisionManager get() = world.collisionManager
    private val anchor: Position = spot.location.anchor.toPosition()
    private val scan = TreeScan(anchor, spot.location.radius)

    override fun isBusy(): Boolean =
        !player.walking.isEmpty || hasBlockingWindow() || FOREGROUND_ACTIONS.any { player.actions.size(it) > 0 }

    override fun look(): WoodcuttingView =
        WoodcuttingView(
            woodcuttingLevel = player.woodcutting.level,
            hasUsableAxe = Axe.computeAxeType(player) != null,
            inventoryFull = player.inventory.isFull,
            logsInInventory = logSlots().size,
            atLocation = scan.atLocation(player.position),
            trees = scan.candidates(player.position, standingTrees(), terrain = this),
        )

    override fun chop(tree: TreeCandidate) {
        val target = find(tree) ?: return
        val event = ObjectFirstClickEvent(player, target)
        if (mayAct(event)) {
            closeWindowsLikeAClick()
            val listeners = player.plugins.pipelines.get(ObjectFirstClickEvent::class.java)
                .getInteractionListeners(player, target, event)
            player.submitAction(InteractionAction(player, listeners, target, event))
        }
    }

    override fun walkTo(tree: TreeCandidate) = walkTo(tree.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        closeWindowsLikeAClick()
        player.navigator.navigate(tile, true)
    }

    override fun dropLogs() {
        for (slot in logSlots()) {
            val log = player.inventory[slot] ?: continue
            val event = DropItemEvent(player, log.id, INVENTORY_WIDGET, slot)
            if (mayAct(event)) {
                player.plugins.post(event)
            }
        }
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    /** A level-up dialogue does not hold up a real player either: their next click closes it. */
    private fun hasBlockingWindow(): Boolean =
        player.overlays.overlayMap.any { (type, overlay) -> type in WINDOW_TYPES && overlay !is LevelUpInterface }

    /** What the walk packet sent before every world click does, without interrupting the current action. */
    private fun closeWindowsLikeAClick() {
        player.overlays.closeWindows(false)
    }

    private fun standingTrees(): List<StandingTree> =
        world.locator.findObjects(anchor, spot.location.radius, ::isStandingTree).map {
            StandingTree(it.id, it.position, maxOf(it.sizeX(), it.sizeY()), TreeStump.TREE_ID_MAP.getValue(it.id).tree)
        }

    private fun isStandingTree(obj: GameObject): Boolean =
        obj.id in spot.treeObjectIds && obj.state == EntityState.ACTIVE && obj.isVisibleTo(player)

    private fun find(tree: TreeCandidate): GameObject? =
        world.objects.findAll(tree.position)
            .filter { it.id == tree.objectId && it.state == EntityState.ACTIVE && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    private fun logSlots(): List<Int> =
        (0 until player.inventory.capacity()).filter { player.inventory[it]?.id in LOG_IDS }

    /** The checks the packet reader makes before dispatching a click. */
    private fun mayAct(event: ControllableEvent): Boolean = !player.isLocked && player.controllers.checkEvent(event)

    override fun canStep(from: Position, direction: Direction): Boolean =
        collision.traversable(from, EntityType.PLAYER, direction)

    override fun isBlocked(tile: Position): Boolean = collision.isBlocked(tile, false)

    override fun reachedFrom(tile: Position, tree: StandingTree): Boolean =
        collision.reached(tile, tree.position, STANDARD_SIZE)

    private companion object {
        const val INVENTORY_WIDGET = 3214
        val LOG_IDS: Set<Int> = Tree.ALL.keys
        val WINDOW_TYPES = setOf(OverlayType.WIDGET_STANDARD, OverlayType.INPUT)

        /** Soft actions (status effects such as poison) run in the background and do not keep a player busy. */
        val FOREGROUND_ACTIONS = ActionType.entries.filter { it != ActionType.SOFT }
    }
}
