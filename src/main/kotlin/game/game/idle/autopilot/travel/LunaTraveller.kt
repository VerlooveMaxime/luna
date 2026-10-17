package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.WebWalkExecutor
import game.idle.autopilot.LunaClicks
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelWeb
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.`object`.GameObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool

/**
 * [Traveller] for a logged-in player. Trips are planned on [web] with what [allowance] lets the planner spend, less what
 * [reserved] holds back for the flow's teleport reflexes, on [planning]'s threads; without the web (its data not
 * generated yet) a trip is the plain walk it was before. Every click goes through the same event and interaction
 * action as the client's, so Luna's doors, ladders and stairs answer it unchanged.
 */
class LunaTraveller(
    private val player: Player,
    private val web: TravelWeb?,
    private val allowance: TravelAllowance = TravelAllowance.NONE,
    private val reserved: Spend = Spend.NONE,
    private val planning: Executor = ForkJoinPool.commonPool(),
) : Traveller {

    override fun position(): Position = player.position

    /** The up-or-down dialogue a climb opens is the trip's own window, which it answers. */
    override fun isBusy(): Boolean = LunaClicks.busyBesides(player, ownWindowOpen = choosing())

    override fun plan(destinations: List<Position>): CompletableFuture<WebWalkPlan?> {
        val start = player.position
        val web = web ?: return CompletableFuture.completedFuture(plainWalk(start, destinations))
        val snapshot = allowance.restrict(BotCapabilities.snapshot(player, web.graph.teleports), reserved, web.graph.teleports)
        return CompletableFuture.supplyAsync({ web.plan(start, destinations, snapshot) }, planning)
    }

    private fun plainWalk(start: Position, destinations: List<Position>): WebWalkPlan {
        val nearest = destinations.minBy { it.computeLongestDistance(start) }
        return WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, start, nearest, cost = 0.0)), cost = 0.0)
    }

    override fun walk(to: Position) = LunaClicks.walk(player, to)

    override fun standing(id: Int, at: Position): Boolean = objectOn(id, at) != null

    override fun use(id: Int, at: Position, option: String?): Boolean {
        val target = objectOn(id, at) ?: return false
        val number = if (option == null) 1 else WebWalkExecutor.optionNumber(target.def().actions, option) ?: return false
        val event: ObjectClickEvent = when (number) {
            1 -> ObjectFirstClickEvent(player, target)
            2 -> ObjectSecondClickEvent(player, target)
            else -> ObjectThirdClickEvent(player, target)
        }
        LunaClicks.interact(player, event, target, event.javaClass)
        return true
    }

    override fun choosing(): Boolean = dialogue() != null

    /** The options' buttons follow the dialogue's title, as Luna's `clickOption.kts` lists them. */
    override fun choose(option: Int) {
        val dialogue = dialogue() ?: return
        LunaClicks.post(player, ButtonClickEvent(player, dialogue.id + 1 + option))
    }

    override fun interrupt() = LunaClicks.interrupt(player)

    private fun dialogue(): OptionDialogue? = player.overlays.getOverlay(OptionDialogue::class.java)

    private fun objectOn(id: Int, at: Position): GameObject? =
        player.world.objects.findAll(at).filter { it.id == id && it.isVisibleTo(player) }.findFirst().orElse(null)
}
