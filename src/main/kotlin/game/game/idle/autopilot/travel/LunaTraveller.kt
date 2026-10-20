package game.idle.autopilot.travel

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.LunaTeleports
import game.idle.flow.EntrancePassage
import game.idle.flow.Place
import game.idle.flow.Said
import game.idle.flow.Speech
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.Teleports
import game.idle.idleState
import game.idle.location.Tile
import game.idle.movement.ObjectForms
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import game.idle.movement.TravelWeb
import game.idle.movement.TravelWebs
import game.idle.movement.shownFlag
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ContinueDialogueEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.OptionDialogue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * [Traveller] for a logged-in player. Trips are planned on [web] with what [allowance] lets the planner spend, less what
 * [reserved] holds back for the flow's teleport reflexes, by [planning]: on the game thread unless told otherwise, since
 * a trip across the map takes 10 to 45 ms to plan (measured 2026-10-17), once per walk, and every decision then sees
 * its plan. While the allowance keeps back something the player could pay with, a second plan with everything allowed
 * says which choices a quicker trip would have needed (the long way round, Q6). Jewellery counts as [LunaTeleports]
 * rubs it; an object that changes form is planned through only when the player sees an option on it ([ObjectForms]).
 * Without the web (its data not generated yet) a trip is the plain walk it was before. Every click goes through the
 * same event and interaction action as the client's, so Luna's doors, ladders, stairs, entrances and toll gate answer
 * it unchanged. What the trip says goes to [speech] at once. A [fleeing] player's walks are found on the game thread and
 * break off what they were doing first, so they are on the move from the next tick and do not turn to fight back; only a
 * click holds them up.
 */
class LunaTraveller(
    private val player: Player,
    private val web: TravelWeb?,
    private val allowance: TravelAllowance = TravelAllowance.NONE,
    private val reserved: Spend = Spend.NONE,
    private val planning: Executor = Executor(Runnable::run),
    private val fleeing: Boolean = false,
    private val speech: Speech = Speech {},
) : Traveller {

    override fun position(): Position = player.position

    /**
     * A dialogue is the trip's own window, which it answers: a climb's up-or-down question, the toll guard's lines. A
     * click on arrival holds the trip.
     */
    override fun isBusy(): Boolean = if (fleeing) LunaClicks.clicking(player) else LunaClicks.busyBesides(player, ownWindowOpen = dialogue() != null)

    override fun plan(destinations: List<Position>): CompletableFuture<TripPlanning> {
        val start = player.position
        val web = web ?: return CompletableFuture.completedFuture(plainWalk(start, destinations)?.let(::PlannedTrip) ?: NoTrip())
        val teleports = web.graph.teleports
        val shown = web.formObjects.filter(ObjectForms.SERVER::showsOption).map(::shownFlag).toSet()
        val snapshot = BotCapabilities.snapshot(player, teleports, flags = shown)
        val carried = CapabilitySnapshot(
            snapshot.skillLevels, snapshot.items, snapshot.flags, snapshot.wildernessLevel, snapshot.usableTeleports, LunaTeleports.charges(player),
            snapshot.teleportSpends,
        )
        val allowed = allowance.restrict(carried, reserved, teleports)
        val everything = allowance.beyond(carried, reserved, teleports)
        return CompletableFuture.supplyAsync({ trip(web, start, destinations, allowed, everything) }, planning)
    }

    /**
     * The trip on what [allowed] lets it spend; with [everything] given, the choices a quicker one spending that would
     * need, or for no trip at all, the first choice one spending that would need.
     */
    private fun trip(web: TravelWeb, start: Position, destinations: List<Position>, allowed: CapabilitySnapshot, everything: CapabilitySnapshot?): TripPlanning {
        val other = everything?.let { web.plan(start, destinations, it) }
        val plan = web.plan(start, destinations, allowed) ?: return NoTrip(other?.let(::forbidden)?.minOrNull())
        // Everything allowed plans whatever the rules allow, so it finds a trip whenever they do.
        val quicker = other ?: plan
        if (quicker.cost >= plan.cost) return PlannedTrip(plan)
        return PlannedTrip(plan, forbidden(quicker))
    }

    /** The Travel choices [plan] spends by that the flow does not allow. */
    private fun forbidden(plan: WebWalkPlan): Set<TravelChoice> = plan.legs.mapNotNull(TravelChoice::of).filterNot(allowance::allows).toSet()

    /** A walk changes no floor, so a destination on another one has no plain walk. */
    private fun plainWalk(start: Position, destinations: List<Position>): WebWalkPlan? {
        val nearest = destinations.filter { it.z == start.z }.minByOrNull { it.computeLongestDistance(start) } ?: return null
        return WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, start, nearest, cost = 0.0)), cost = 0.0)
    }

    override fun walk(to: Position) {
        breakOffForFlight()
        LunaClicks.walk(player, to, async = !fleeing)
    }

    override fun standing(id: Int, at: Position): Boolean = LunaClicks.objectOn(player, id, at) != null

    override fun entrance(id: Int, at: Position): EntrancePassage? = LunaEntrances.passage(id, at)

    /** [LunaClicks.use]: out of reach, the player walks up to the object first and clicks on arrival. */
    override fun use(id: Int, at: Position, option: String?, from: Position): Boolean {
        breakOffForFlight()
        return LunaClicks.use(player, id, at, option, from, async = !fleeing)
    }

    /** A flight breaks off what the player was doing first, a fight they turned to as they were hit included. */
    private fun breakOffForFlight() {
        if (fleeing) player.actions.interruptWeak()
    }

    override fun choosing(): Boolean = dialogue() is OptionDialogue

    /** The options' buttons follow the dialogue's title, as Luna's `clickOption.kts` lists them. */
    override fun choose(option: Int) {
        val dialogue = dialogue() as? OptionDialogue ?: return
        LunaClicks.post(player, ButtonClickEvent(player, dialogue.id + 1 + option))
    }

    override fun options(): List<String> = (dialogue() as? OptionDialogue)?.options.orEmpty()

    /** The crew is looked for as upstream's `ShipVoyage` looks for it, nearest first. */
    override fun talkTo(ids: Set<Int>, near: Position): Boolean {
        val npc = player.world.locator.findNpcs(near, CREW_REACH, true) { it.id in ids }.firstOrNull() ?: return false
        LunaClicks.talkTo(player, npc)
        return true
    }

    override fun talking(): Boolean = dialogue().let { it != null && it !is OptionDialogue }

    /** As the client's "Click here to continue". */
    override fun talkOn() {
        val dialogue = dialogue() ?: return
        player.plugins.post(ContinueDialogueEvent(player, dialogue.id))
    }

    override fun teleport(
        teleport: TeleportDefinition,
        starting: (TeleportStarted) -> Unit,
        notWornAgain: (TeleportDestination.Jewellery) -> Unit,
    ): TeleportStarted? {
        val destination = Teleports.of(teleport)?.takeIf { LunaTeleports.canTeleport(player, it) } ?: return null
        return LunaTeleports.teleport(player, destination, starting, notWornAgain)
    }

    override fun say(said: Said) = speech.say(said)

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun dialogue(): DialogueInterface? = player.overlays.getOverlay(DialogueInterface::class.java)

    private companion object {
        /** How far from where a ship is boarded its crew is looked for, upstream's `ShipVoyage.CREW_RADIUS`. */
        const val CREW_REACH = 10
    }
}

/** What the running step's trips say at once (`LunaAutopilotPlayer.activity` sets it for each step); never saved. */
var Player.stepSpeech by Attr.nullableObj(Speech::class)

/**
 * The traveller of [player]'s steps on the server's travel web: what the player's flow lets its trips spend (Q1), less
 * the next cast of each of its teleport reflexes (Q2), speaking as the running step.
 */
fun travellerFor(player: Player): LunaTraveller =
    LunaTraveller(player, TravelWebs.SERVER.of(player.world), player.idleState.travel, LunaTeleports.reserve(player), speech = player.stepSpeech ?: Speech {})

/** A trip for [player] to [target] on the server's travel web, its block saying [place]. */
fun tripTo(player: Player, target: Tile, place: Place): TripWork = TripWork(travellerFor(player), target, place)
