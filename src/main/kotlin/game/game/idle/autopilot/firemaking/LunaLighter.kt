package game.idle.autopilot.firemaking

import api.predef.firemaking
import game.idle.autopilot.LunaClicks
import game.skill.firemaking.Firemaking
import game.skill.firemaking.Log
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType

/**
 * [Lighter] for a logged-in player and the logs in [logIds]. Lighting posts the same event as the client's
 * "use tinderbox on logs", so Luna's firemaking runs unchanged; a tile is free when Luna's own check would let a
 * fire be lit on it.
 */
class LunaLighter(private val player: Player, private val logIds: Set<Int>) : Lighter {

    private val world get() = player.world

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

    /** The navigator's step checks that the tile can be walked onto; [free] adds that no fire or scenery is there. */
    override fun stepAside(): Boolean =
        ASIDE.any { direction -> free(player.position.translate(1, direction)) && player.navigator.step(direction) }

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
        val ASIDE = listOf(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH)
        val BLOCKING = setOf(
            ObjectType.DEFAULT, ObjectType.STRAIGHT_WALL, ObjectType.DIAGONAL_WALL, ObjectType.WALL_CORNER,
            ObjectType.DIAGONAL_CORNER_WALL,
        )
    }
}
