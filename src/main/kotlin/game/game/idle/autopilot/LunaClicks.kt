package game.idle.autopilot

import engine.widget.skill.LevelUpInterface
import io.luna.game.action.ActionType
import io.luna.game.event.Event
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.model.Entity
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.overlay.OverlayType

/** What every autopilot adapter does with a real player: click things like the client and tell busy from idle. */
object LunaClicks {

    /** Soft actions (status effects such as poison) run in the background and do not keep a player busy. */
    private val FOREGROUND_ACTIONS = ActionType.entries.filter { it != ActionType.SOFT }

    private val WINDOW_TYPES = setOf(OverlayType.WIDGET_STANDARD, OverlayType.INPUT)

    /** Walking or running a non-soft action; windows are the activity's own concern. */
    fun isActing(player: Player): Boolean =
        !player.walking.isEmpty || FOREGROUND_ACTIONS.any { player.actions.size(it) > 0 }

    /**
     * A window the player has open, which a real player would close before walking off. A level-up dialogue does not
     * hold a real player up: their next click closes it.
     */
    fun hasBlockingWindow(player: Player): Boolean =
        player.overlays.overlayMap.any { (type, overlay) -> type in WINDOW_TYPES && overlay !is LevelUpInterface }

    /** The checks the packet reader makes before dispatching a click. */
    fun mayAct(player: Player, event: ControllableEvent): Boolean =
        !player.isLocked && player.controllers.checkEvent(event)

    /**
     * Runs [event] on [target] (an object, an npc) through the same interaction action a client click builds, so the
     * target's own listeners, reach checks and animations apply unchanged. Windows are closed first, without
     * interrupting the current action, as the walk packet before every world click does.
     */
    fun <E> interact(player: Player, event: E, target: Entity, type: Class<E>)
        where E : Event, E : ControllableEvent, E : InteractableEvent {
        if (mayAct(player, event)) {
            player.overlays.closeWindows(false)
            val listeners = player.plugins.pipelines.get(type).getInteractionListeners(player, target, event)
            player.submitAction(InteractionAction(player, listeners, target, event))
        }
    }
}
