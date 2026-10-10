package game.idle.autopilot.firemaking

import api.predef.firemaking
import game.idle.autopilot.LunaClicks
import game.idle.movement.TileBounds
import game.idle.movement.WalkingDistances
import game.skill.firemaking.Firemaking
import game.skill.firemaking.Log
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType

/**
 * [Lighter] for a logged-in player and the logs in [logIds], from where the player stands when the step starts.
 * Lighting posts the same event as the client's "use tinderbox on logs", so Luna's firemaking runs unchanged; a tile
 * is free when Luna's own check would let a fire be lit on it.
 */
class LunaLighter(private val player: Player, private val logIds: Set<Int>) : Lighter {

    private val world get() = player.world

    /** The spot the step started on, which the fires stay near. */
    private val start: Position = player.position

    override fun isBusy(): Boolean = LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)

    override fun look(): LightView =
        LightView(
            hasTinderbox = player.inventory.contains(Firemaking.TINDERBOX),
            logs = logIds.sumOf { player.inventory.computeAmountForId(it) },
            lightable = (0 until player.inventory.capacity()).firstOrNull { lightable(player.inventory[it]?.id) },
            tileFree = free(player.position),
        )

    override fun light(slot: Int) {
        val log = player.inventory[slot] ?: return
        val tinderbox = player.inventory.computeIndexForId(Firemaking.TINDERBOX)
        val event = ItemOnItemEvent(player, Firemaking.TINDERBOX, log.id, tinderbox, slot, INVENTORY, INVENTORY)
        if (LunaClicks.mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

    /**
     * Walks to the free tile a walk from the start reaches first, at most [AROUND] steps from it (Maxime, 2026-10-10:
     * "not wander from the starting position"), however many fires burn around the player.
     */
    override fun moveToFreeTile(): Boolean {
        val area = TileBounds.around(start, AROUND)
        val free = (area.minX..area.maxX).flatMap { x -> (area.minY..area.maxY).map { y -> Position(x, y, start.z) } }.filter(::free)
        val target = WalkingDistances.firstReached(start, free.toSet(), AROUND) { from, direction ->
            world.collisionManager.traversable(from, EntityType.PLAYER, direction)
        } ?: return false
        player.navigator.navigate(target, true)
        return true
    }

    private fun lightable(id: Int?): Boolean {
        val log = LOGS[id] ?: return false
        return id in logIds && log.level <= player.firemaking.level
    }

    /** No fire, scenery or wall on the tile, as `LightLogAction` checks. */
    private fun free(tile: Position): Boolean =
        world.objects.findAll(tile).noneMatch { it.objectType in BLOCKING }

    private companion object {
        const val INVENTORY = 3214
        val LOGS: Map<Int, Log> = Log.entries.associateBy { it.id }
        const val AROUND = 10
        val BLOCKING = setOf(
            ObjectType.DEFAULT, ObjectType.STRAIGHT_WALL, ObjectType.DIAGONAL_WALL, ObjectType.WALL_CORNER,
            ObjectType.DIAGONAL_CORNER_WALL,
        )
    }
}
