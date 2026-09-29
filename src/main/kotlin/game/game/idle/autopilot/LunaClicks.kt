package game.idle.autopilot

import io.luna.game.action.ActionType
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.`object`.GameObject

/** What every autopilot adapter does with a real player: click objects like the client and tell busy from idle. */
object LunaClicks {

    /** Soft actions (status effects such as poison) run in the background and do not keep a player busy. */
    private val FOREGROUND_ACTIONS = ActionType.entries.filter { it != ActionType.SOFT }

    /** Walking or running a non-soft action; windows are the activity's own concern. */
    fun isActing(player: Player): Boolean =
        !player.walking.isEmpty || FOREGROUND_ACTIONS.any { player.actions.size(it) > 0 }

    /** The checks the packet reader makes before dispatching a click. */
    fun mayAct(player: Player, event: ControllableEvent): Boolean =
        !player.isLocked && player.controllers.checkEvent(event)

    /**
     * Runs [event] on [target] through the same interaction action a client click builds, so the object's own
     * listeners, reach checks and animations apply unchanged. Windows are closed first, without interrupting the
     * current action, as the walk packet before every world click does.
     */
    fun <E : ObjectClickEvent> clickObject(player: Player, event: E, target: GameObject, type: Class<E>) {
        if (mayAct(player, event)) {
            player.overlays.closeWindows(false)
            val listeners = player.plugins.pipelines.get(type).getInteractionListeners(player, target, event)
            player.submitAction(InteractionAction(player, listeners, target, event))
        }
    }
}
