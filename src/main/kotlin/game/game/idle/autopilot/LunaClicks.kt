package game.idle.autopilot

import engine.widget.skill.LevelUpInterface
import game.idle.movement.approachTiles
import game.idle.movement.canStandOn
import io.luna.game.action.ActionType
import io.luna.game.event.Event
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.model.Direction
import io.luna.game.model.Entity
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.overlay.OverlayType

/** What every autopilot adapter does with a real player: click things like the client and tell busy from idle. */
object LunaClicks {

    /** The bag's widget, which every click on a carried item names. */
    const val INVENTORY = 3214

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

    /** Busy as most kinds see it: acting, or a window open that the player would close first. */
    fun busy(player: Player): Boolean = isActing(player) || hasBlockingWindow(player)

    /** [busy], except that the kind's own window, open when [ownWindowOpen], is what the kind answers and never in the way. */
    fun busyBesides(player: Player, ownWindowOpen: Boolean): Boolean =
        isActing(player) || (hasBlockingWindow(player) && !ownWindowOpen)

    /**
     * Walking up to the target of a click, or about to reach it: the click's interaction action is still queued. What a
     * fight or a pick-up watches instead of [isActing]: an attack and a take are instant events (`InteractionAction`
     * runs their listener as soon as the target is reached), so no skill action follows for the kind to watch, and
     * combat's own action is WEAK and lingers after the kill.
     */
    fun interacting(player: Player): Boolean =
        !player.walking.isEmpty || player.actions.first(InteractionAction::class.java) != null

    /**
     * Breaks off what the autopilot has the player doing: the action in progress as a player's own click would, leaving
     * a strong one (a death) alone, and the walk it started.
     */
    fun interrupt(player: Player) {
        endWalk(player)
        player.actions.interruptWeak()
    }

    /**
     * Ends the walk the autopilot started, if any: every walk of its goes through the navigator, a walk the player clicks
     * never does. Cancelling the navigator leaves the path in the walking queue, so that is cleared too; it must come
     * before the walk's action is interrupted, which ends the request and would hide the walk.
     */
    fun endWalk(player: Player) {
        if (player.navigator.isActive) {
            player.navigator.cancel()
            player.walking.clear()
        }
    }

    /**
     * Walks [player] to [tile] as a click would: windows closed first without interrupting the action in progress, then
     * the navigator. A tile something stands on is swapped for the nearest free tile next to it, because a path to a
     * blocked tile makes the pathfinder search its whole node budget before giving up.
     */
    fun walk(player: Player, tile: Position) {
        val collision = player.world.collisionManager
        val goal = if (canStandOn(collision, tile)) {
            tile
        } else {
            approachTiles(tile, width = 1, height = 1, from = player.position).firstOrNull { canStandOn(collision, it) } ?: tile
        }
        player.overlays.closeWindows(false)
        player.navigator.navigate(goal, true)
    }

    /**
     * Whether a player can step from [from] in [direction]. The reach scans walk by this, so a closed door is a wall to
     * them while the navigator walks through doors (Maxime, 2026-10-12: the scans should see through doors once the
     * navigator handles doors, portals, levers and teleports, `.memory/tasks/world-doors/`).
     */
    fun canStep(world: World, from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    /** The checks the packet reader makes before dispatching a click. */
    fun mayAct(player: Player, event: ControllableEvent): Boolean =
        !player.isLocked && player.controllers.checkEvent(event)

    /** Posts [event] to its listeners as the packet reader does, when the player may act. */
    fun <E> post(player: Player, event: E) where E : Event, E : ControllableEvent {
        if (mayAct(player, event)) {
            player.plugins.post(event)
        }
    }

    /** [post], with the open windows closed first without interrupting the current action, as a click on a carried item does. */
    fun <E> postClosing(player: Player, event: E) where E : Event, E : ControllableEvent {
        if (mayAct(player, event)) {
            player.overlays.closeWindows(false)
            player.plugins.post(event)
        }
    }

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
