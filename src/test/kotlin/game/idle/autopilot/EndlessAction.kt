package game.idle.autopilot

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/** An action that never finishes on its own, like a skill action that keeps going. */
class EndlessAction(player: Player, type: ActionType = ActionType.WEAK) : Action<Player>(player, type) {
    override fun run(): Boolean = false
}
