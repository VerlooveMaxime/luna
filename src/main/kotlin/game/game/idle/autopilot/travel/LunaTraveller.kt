package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.autopilot.LunaClicks
import game.idle.flow.Place
import game.idle.location.Tile
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelWeb
import game.idle.movement.TravelWebs
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * [Traveller] for a logged-in player. Trips are planned on [web] with what [allowance] lets the planner spend, less what
 * [reserved] holds back for the flow's teleport reflexes, by [planning]: on the game thread unless told otherwise, since
 * a trip across the map takes 10 to 45 ms to plan (measured 2026-10-17), once per walk, and every decision then sees
 * its plan. Without the web (its data not generated yet) a trip is the plain walk it was before. Every click goes
 * through the same event and interaction action as the client's, so Luna's doors, ladders and stairs answer it
 * unchanged.
 */
class LunaTraveller(
    private val player: Player,
    private val web: TravelWeb?,
    private val allowance: TravelAllowance = TravelAllowance.NONE,
    private val reserved: Spend = Spend.NONE,
    private val planning: Executor = Executor(Runnable::run),
) : Traveller {

    override fun position(): Position = player.position

    /** The up-or-down dialogue a climb opens is the trip's own window, which it answers; a click on arrival holds the trip. */
    override fun isBusy(): Boolean = LunaClicks.busyBesides(player, ownWindowOpen = choosing())

    override fun plan(destinations: List<Position>): CompletableFuture<WebWalkPlan?> {
        val start = player.position
        val web = web ?: return CompletableFuture.completedFuture(plainWalk(start, destinations))
        val snapshot = allowance.restrict(BotCapabilities.snapshot(player, web.graph.teleports), reserved, web.graph.teleports)
        return CompletableFuture.supplyAsync({ web.plan(start, destinations, snapshot) }, planning)
    }

    /** A walk changes no floor, so a destination on another one has no plain walk. */
    private fun plainWalk(start: Position, destinations: List<Position>): WebWalkPlan? {
        val nearest = destinations.filter { it.z == start.z }.minByOrNull { it.computeLongestDistance(start) } ?: return null
        return WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, start, nearest, cost = 0.0)), cost = 0.0)
    }

    override fun walk(to: Position) = LunaClicks.walk(player, to)

    override fun standing(id: Int, at: Position): Boolean = LunaClicks.objectOn(player, id, at) != null

    /** [LunaClicks.use]: out of reach, the player walks up to the object first and clicks on arrival. */
    override fun use(id: Int, at: Position, option: String?, from: Position): Boolean = LunaClicks.use(player, id, at, option, from)

    override fun choosing(): Boolean = dialogue() != null

    /** The options' buttons follow the dialogue's title, as Luna's `clickOption.kts` lists them. */
    override fun choose(option: Int) {
        val dialogue = dialogue() ?: return
        LunaClicks.post(player, ButtonClickEvent(player, dialogue.id + 1 + option))
    }

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun dialogue(): OptionDialogue? = player.overlays.getOverlay(OptionDialogue::class.java)
}

/** A trip for [player] to [target] on the server's travel web, its block saying [place]. */
fun tripTo(player: Player, target: Tile, place: Place): TripWork =
    TripWork(LunaTraveller(player, TravelWebs.SERVER.of(player.world)), target, place)
