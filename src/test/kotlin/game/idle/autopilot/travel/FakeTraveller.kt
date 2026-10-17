package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import io.luna.game.model.Position
import java.util.concurrent.CompletableFuture

/**
 * Records what a trip asks of the player; [position] is where they stand. A plan is the next of [plans], or with none
 * queued a plain walk to the first destination, none when [noWay].
 */
class FakeTraveller(var position: Position) : Traveller {
    var busy = false
    var choosing = false
    var usable = true
    var noWay = false
    val standing = mutableSetOf<Pair<Int, Position>>()
    val plans = ArrayDeque<CompletableFuture<WebWalkPlan?>>()
    val planned = mutableListOf<List<Position>>()
    val walks = mutableListOf<Position>()
    val used = mutableListOf<Triple<Int, Position, String?>>()
    val sides = mutableListOf<Position>()
    val chosen = mutableListOf<Int>()
    var interrupted = 0

    override fun position(): Position = position

    override fun isBusy(): Boolean = busy

    override fun plan(destinations: List<Position>): CompletableFuture<WebWalkPlan?> {
        planned += destinations
        return plans.removeFirstOrNull() ?: CompletableFuture.completedFuture(if (noWay) null else plainWalk(destinations.first()))
    }

    private fun plainWalk(to: Position) = WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, position, to, cost = 1.0)), cost = 1.0)

    override fun walk(to: Position) {
        walks += to
    }

    override fun standing(id: Int, at: Position): Boolean = id to at in standing

    override fun use(id: Int, at: Position, option: String?, from: Position): Boolean {
        used += Triple(id, at, option)
        sides += from
        return usable
    }

    override fun choosing(): Boolean = choosing

    override fun choose(option: Int) {
        chosen += option
    }

    override fun interrupt() {
        interrupted++
    }
}
