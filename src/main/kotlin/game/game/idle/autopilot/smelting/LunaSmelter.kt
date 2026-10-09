package game.idle.autopilot.smelting

import api.predef.smithing
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.PlaceCandidate
import game.idle.location.Area
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.idle.movement.footprintOf
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Smelter] for a logged-in player smelting [bar] at furnaces within [area]. The ore is used on the furnace through
 * the same interaction as the client's, so Luna's smelting runs unchanged and smelts every bar the ores make.
 */
class LunaSmelter(private val player: Player, private val bar: BarType, private val area: Area) : Smelter, ReachTerrain<GameObject> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)

    /** Luna smelts the bar its ore names; iron ore names iron, so steel is smelted from its coal. */
    private val oreToUse: Int = if (bar == BarType.STEEL) COAL else bar.oreRequired.first.id

    override fun isBusy(): Boolean = LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)

    override fun look(): SmeltingView =
        SmeltingView(
            oreSlot = if (hasOreForABar()) player.inventory.computeIndexForId(oreToUse) else null,
            smithingLevel = player.smithing.level,
            atLocation = scan.atLocation(player.position),
            furnaces = scan.reachable(player.position, furnaces(), ::footprintOf, terrain = this)
                .map { (furnace, reach) -> PlaceCandidate.of(furnace, reach) },
        )

    override fun smelt(furnace: PlaceCandidate, slot: Int) {
        val target = find(furnace) ?: return
        val ore = player.inventory[slot] ?: return
        LunaClicks.interact(player, ItemOnObjectEvent(player, ore.id, slot, INVENTORY, target), target, ItemOnObjectEvent::class.java)
    }

    override fun walkTo(furnace: PlaceCandidate) = walkTo(furnace.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }

    override fun bars(): Int = player.inventory.computeAmountForId(bar.id)

    override fun stop() {
        player.actions.interruptWeak()
    }

    private fun hasOreForABar(): Boolean = listOfNotNull(bar.oreRequired.first, bar.oreRequired.second).all(::carries)

    private fun carries(ore: Item): Boolean = player.inventory.computeAmountForId(ore.id) >= ore.amount

    // Objects found through their chunk are always ACTIVE, so only the id needs checking.
    private fun furnaces(): List<GameObject> =
        world.locator.findObjects(anchor, area.radius) { it.id in Smithing.FURNACE_OBJECTS && it.isVisibleTo(player) }.toList()

    private fun find(furnace: PlaceCandidate): GameObject? =
        world.objects.findAll(furnace.position)
            .filter { it.id == furnace.objectId && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: GameObject): Boolean =
        world.collisionManager.reached(tile, target, STANDARD_SIZE)

    private companion object {
        const val INVENTORY = 3214
        const val COAL = 453
    }
}
