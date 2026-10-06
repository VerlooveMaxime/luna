package game.idle.autopilot.mining

import api.predef.mining
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.idle.movement.footprintOf
import game.skill.mining.Ore
import game.skill.mining.Pickaxe
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Miner] for a logged-in player mining [ore] within [area]. Rocks are clicked through the same interaction as the
 * client's "Mine", so Luna's mining runs unchanged; an emptied rock turns into another object and drops out of the
 * search until it fills again.
 */
class LunaMiner(private val player: Player, private val ore: Ore, private val area: Area) : Miner, ReachTerrain<GameObject> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private val rockIds: Set<Int> = Ore.ORE_MAP.get(ore)

    override fun isBusy(): Boolean = LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)

    override fun look(): MiningView =
        MiningView(
            miningLevel = player.mining.level,
            hasUsablePickaxe = Pickaxe.computePickType(player) != null,
            inventoryFull = player.inventory.isFull,
            atLocation = scan.atLocation(player.position),
            rocks = scan.reachable(player.position, rocks(), ::footprintOf, terrain = this)
                .map { (rock, reach) -> RockCandidate(rock.id, rock.position, reach.distance, reach.usableFromHere, reach.approach) },
        )

    override fun mine(rock: RockCandidate) {
        val target = find(rock) ?: return
        LunaClicks.interact(player, ObjectFirstClickEvent(player, target), target, ObjectFirstClickEvent::class.java)
    }

    override fun walkTo(rock: RockCandidate) = walkTo(rock.approach)

    override fun walkToLocation() = walkTo(anchor)

    private fun walkTo(tile: Position) {
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }

    override fun ores(): Int = player.inventory.computeAmountForId(ore.item)

    override fun stop() {
        player.actions.interruptWeak()
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    // Objects found through their chunk are always ACTIVE, so only the id needs checking.
    private fun rocks(): List<GameObject> =
        world.locator.findObjects(anchor, area.radius) { it.id in rockIds && it.isVisibleTo(player) }.toList()

    /** A rock emptied since the look is gone: its tile now holds another object. */
    private fun find(rock: RockCandidate): GameObject? =
        world.objects.findAll(rock.position)
            .filter { it.id == rock.objectId && it.isVisibleTo(player) }
            .findFirst().orElse(null)

    override fun canStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    override fun reachedFrom(tile: Position, target: GameObject): Boolean =
        world.collisionManager.reached(tile, target, STANDARD_SIZE)
}
