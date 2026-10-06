package game.idle.autopilot.cooking

import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.footprintOf
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.skill.cooking.cookFood.Cooking
import game.skill.cooking.cookFood.CookingInterface
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Cooker] for a logged-in player cooking [rawIds] on fires and ranges within [area]. Using the food and picking
 * "cook all" go through the same interaction and button events as the client's clicks, so Luna's cooking runs
 * unchanged.
 */
class LunaCooker(private val player: Player, private val rawIds: Set<Int>, private val area: Area) : Cooker, ReachTerrain<GameObject> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)

    /** The cooking window is the point of this step, so it does not count as a window in the way. */
    override fun isBusy(): Boolean =
        LunaClicks.isActing(player) || (LunaClicks.hasBlockingWindow(player) && !cookingWindowOpen())

    override fun look(): CookingView =
        CookingView(
            rawSlot = (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in rawIds },
            windowOpen = cookingWindowOpen(),
            atLocation = scan.atLocation(player.position),
            places = scan.reachable(player.position, places(), ::footprintOf, terrain = this)
                .map { (place, reach) -> PlaceCandidate.of(place, reach) },
        )

    override fun useOn(place: PlaceCandidate, slot: Int) {
        val target = find(place) ?: return
        val item = player.inventory[slot] ?: return
        LunaClicks.interact(player, ItemOnObjectEvent(player, item.id, slot, INVENTORY, target), target, ItemOnObjectEvent::class.java)
    }

    override fun cookAll() {
        val event = ButtonClickEvent(player, COOK_ALL)
        if (LunaClicks.mayAct(player, event)) {
            player.plugins.post(event)
        }
    }

    override fun walkTo(place: PlaceCandidate) = walkTo(place.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }

    override fun raw(): Int = rawIds.sumOf { player.inventory.computeAmountForId(it) }

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun cookingWindowOpen(): Boolean = player.overlays.has(CookingInterface::class.java)

    // Objects found through their chunk are always ACTIVE, so only the id needs checking.
    private fun places(): List<GameObject> =
        world.locator.findObjects(anchor, area.radius) { it.id in Cooking.COOKING_OBJECTS && it.isVisibleTo(player) }.toList()

    private fun find(place: PlaceCandidate): GameObject? =
        world.objects.findAll(place.position)
            .filter { it.id == place.objectId && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: GameObject): Boolean =
        world.collisionManager.reached(tile, target, STANDARD_SIZE)

    private companion object {
        const val INVENTORY = 3214

        /** The cooking window's "all" button (`cookFood.kts`). */
        const val COOK_ALL = 13717
    }
}
