package game.idle.autopilot.walk

import game.idle.autopilot.LunaClicks
import game.idle.location.Tile
import io.luna.game.model.mob.Player

/** [Walker] for a logged-in player. */
class LunaWalker(private val player: Player, private val target: Tile) : Walker {

    override fun isBusy(): Boolean = LunaClicks.busy(player)

    override fun position(): Tile = Tile.of(player.position)

    override fun walk() = LunaClicks.walk(player, target.toPosition())

    override fun interrupt() = LunaClicks.interrupt(player)
}
