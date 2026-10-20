package game.idle.autopilot

import api.attr.Attr
import api.bot.webwalk.walk.WebWalkExecutor
import api.predef.ext.scheduleOnce
import engine.widget.skill.LevelUpInterface
import game.idle.movement.Doorways
import game.idle.movement.ReachSteps
import game.idle.movement.approachTiles
import game.idle.movement.canStandOn
import game.idle.movement.reachingTiles
import io.luna.game.action.ActionType
import io.luna.game.event.Event
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture

/** What every autopilot adapter does with a real player: click things like the client and tell busy from idle. */
object LunaClicks {

    /** The bag's widget, which every click on a carried item names. */
    const val INVENTORY = 3214

    /** Soft actions (status effects such as poison) run in the background and do not keep a player busy. */
    private val FOREGROUND_ACTIONS = ActionType.entries.filter { it != ActionType.SOFT }

    private val WINDOW_TYPES = setOf(OverlayType.WIDGET_STANDARD, OverlayType.INPUT)

    /** A click [use] or [talkTo] holds for the tick after the walk up to its target ends, made on a target still there. */
    private sealed interface HeldClick {

        fun land(player: Player)

        /** Option [number] of the object of [id] on [at]. */
        class OnObject(val id: Int, val at: Position, val number: Int) : HeldClick {
            override fun land(player: Player) {
                objectOn(player, id, at)?.let { click(player, it, number) }
            }
        }

        /** The first option of [npc]. */
        class OnNpc(val npc: Npc) : HeldClick {
            override fun land(player: Player) {
                if (npc.state == EntityState.ACTIVE) talk(player, npc)
            }
        }
    }

    private var Player.heldClick by Attr.nullableObj(HeldClick::class)

    /** Walking, about to click on arrival or running a non-soft action; windows are the activity's own concern. */
    fun isActing(player: Player): Boolean =
        !player.walking.isEmpty || player.heldClick != null || FOREGROUND_ACTIONS.any { player.actions.size(it) > 0 }

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
    fun interacting(player: Player): Boolean = !player.walking.isEmpty || clicking(player)

    /** About to click on arrival, or a click's interaction action still queued: what a flight waits for, never its walks. */
    fun clicking(player: Player): Boolean = player.heldClick != null || player.actions.first(InteractionAction::class.java) != null

    /**
     * Breaks off what the autopilot has the player doing: the action in progress as a player's own click would, leaving
     * a strong one (a death) alone, the walk it started and a click held for that walk's end.
     */
    fun interrupt(player: Player) {
        player.heldClick = null
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
     * the navigator, its path found off the game thread unless not [async]. A tile something stands on is swapped for the
     * nearest free tile next to it, because a path to a blocked tile makes the pathfinder search its whole node budget
     * before giving up.
     */
    fun walk(player: Player, tile: Position, async: Boolean = true) {
        val collision = player.world.collisionManager
        val goal = if (canStandOn(collision, tile)) {
            tile
        } else {
            approachTiles(tile, width = 1, height = 1, from = player.position).firstOrNull { canStandOn(collision, it) } ?: tile
        }
        player.overlays.closeWindows(false)
        player.navigator.navigate(goal, async)
    }

    /**
     * Whether collision lets a player step from [from] in [direction]: a closed door is a wall here, as it is to the
     * navigator. The reach scans and the flight step through doors by [reachSteps]; what looks for a free tile rather
     * than a way walks by this (where to light a fire).
     */
    fun canStep(world: World, from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.PLAYER, direction)

    /** The steps of a reach scan in [world]: [canStep]'s, and through the shut ones of [doorways], which a walk opens. */
    fun reachSteps(world: World, doorways: Doorways): ReachSteps =
        ReachSteps({ from, direction -> canStep(world, from, direction) }, doorways)

    /** The object of [id] on [at] that [player] sees, if one stands there. */
    fun objectOn(player: Player, id: Int, at: Position): GameObject? =
        player.world.objects.findAll(at).filter { it.id == id && it.isVisibleTo(player) }.findFirst().orElse(null)

    /**
     * Clicks [option] of the object of [id] on [at] for [player], its first one when null, as the client does: Luna's
     * interaction never walks, it only checks the reach while the player walks (web-walker movement Part 3's live check:
     * a door clicked from two tiles inside said "I can't reach that!"), and a click interrupts a walk queued with it. So
     * out of reach the player walks to the tile it is used from nearest [from] first and the click follows in the tick
     * after they get there, the player [isActing] until it lands. A walk cut short (another walk), an [interrupt] (a
     * Stop) or the object gone by then (a door someone else opened) drops the click. The walk's path is found off the
     * game thread unless not [async]. False when the object is not there, has no such option or no tile reaches it.
     */
    fun use(player: Player, id: Int, at: Position, option: String?, from: Position, async: Boolean = true): Boolean {
        val target = objectOn(player, id, at) ?: return false
        val number = if (option == null) 1 else WebWalkExecutor.optionNumber(target.def().actions, option) ?: return false
        val collision = player.world.collisionManager
        if (collision.reached(player, target, STANDARD_SIZE)) {
            click(player, target, number)
            return true
        }
        val tile = reachingTiles(collision, target, from).firstOrNull() ?: return false
        hold(player, HeldClick.OnObject(id, at, number), player.navigator.navigate(tile, async))
        return true
    }

    /**
     * Talks to [npc] as the client's first option on it does, walking into its reach first as [use] walks up to an
     * object (Luna's navigator keeps after an npc that wanders off meanwhile); the click is dropped as [use]'s is, and
     * when the npc is gone by then.
     */
    fun talkTo(player: Player, npc: Npc, async: Boolean = true) {
        if (player.world.collisionManager.reached(player, npc, STANDARD_SIZE)) {
            talk(player, npc)
            return
        }
        hold(player, HeldClick.OnNpc(npc), player.navigator.navigate(npc, async, false))
    }

    private fun hold(player: Player, held: HeldClick, walk: CompletableFuture<NavigationResult>) {
        player.heldClick = held
        walk.whenComplete { result, _ ->
            if (result == NavigationResult.REACHED) {
                player.world.scheduleOnce(1) { arrived(player, held) }
            } else {
                drop(player, held)
            }
        }
    }

    /** Makes [held] unless it was dropped meanwhile or another click is held now. */
    private fun arrived(player: Player, held: HeldClick) {
        if (player.heldClick !== held) return
        player.heldClick = null
        held.land(player)
    }

    private fun talk(player: Player, npc: Npc) = interact(player, NpcFirstClickEvent(player, npc), npc, NpcFirstClickEvent::class.java)

    private fun drop(player: Player, held: HeldClick) {
        if (player.heldClick === held) player.heldClick = null
    }

    private fun click(player: Player, target: GameObject, number: Int) {
        val event: ObjectClickEvent = when (number) {
            1 -> ObjectFirstClickEvent(player, target)
            2 -> ObjectSecondClickEvent(player, target)
            else -> ObjectThirdClickEvent(player, target)
        }
        interact(player, event, target, event.javaClass)
    }

    /** The checks the packet reader makes before dispatching a click: a stun stops clicks on the world, not eating or buttons. */
    fun mayAct(player: Player, event: ControllableEvent): Boolean =
        !player.isActionLocked && !(player.status.isStunned() && event is InteractableEvent) && player.controllers.checkEvent(event)

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
