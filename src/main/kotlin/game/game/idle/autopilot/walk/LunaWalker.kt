package game.idle.autopilot.walk

import game.idle.autopilot.LunaClicks
import game.idle.location.Tile
import game.idle.movement.approachTiles
import game.idle.movement.canStandOn
import io.luna.game.model.mob.Player

/**
 * [Walker] for a logged-in player. Like the woodcutter, it waits while the player has a window open. A target tile
 * something stands on is swapped for the nearest free tile next to it, because a path to a blocked tile makes the
 * pathfinder search its whole node budget before giving up.
 */
class LunaWalker(private val player: Player, private val target: Tile) : Walker {

    override fun isBusy(): Boolean = LunaClicks.isActing(player) || LunaClicks.hasBlockingWindow(player)

    override fun position(): Tile = Tile.of(player.position)

    override fun walk() {
        val collision = player.world.collisionManager
        val goal = target.toPosition()
        val tile = if (canStandOn(collision, goal)) {
            goal
        } else {
            approachTiles(goal, width = 1, height = 1, from = player.position).firstOrNull { canStandOn(collision, it) } ?: goal
        }
        player.overlays.closeWindows(false)
        player.navigator.navigate(tile, true)
    }
}
