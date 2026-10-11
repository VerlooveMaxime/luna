package game.idle.autopilot

import game.idle.flow.Carried
import io.luna.game.model.mob.Player

/** [Carried] for a logged-in player: boosted levels, as Luna's own tool and level checks read them. */
class LunaCarried(private val player: Player) : Carried {

    override fun level(skill: Int): Int = player.skill(skill).level

    override fun holds(id: Int): Boolean = player.inventory.contains(id)

    override fun wears(id: Int): Boolean = player.equipment.contains(id)

    override fun bagFull(): Boolean = player.inventory.isFull
}
