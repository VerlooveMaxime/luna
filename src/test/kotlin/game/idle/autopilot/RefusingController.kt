package game.idle.autopilot

import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController

/** Vetoes every click, as the tutorial does for the ones it scripts. */
class RefusingController(player: Player) : PlayerController(player) {
    override fun event(event: ControllableEvent): Boolean = false
}
