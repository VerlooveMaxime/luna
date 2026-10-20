package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.flow.EntrancePassage
import game.idle.flow.Said
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import java.util.concurrent.CompletableFuture

/**
 * Records what a trip asks of the player; [position] is where they stand. A plan is the next of [plans], or with none
 * queued a plain walk to the first destination, none when [noWay]. Each walk is written to [log] too, as `walk to x,y`,
 * so a kind's test reads its walks among its adapter's steps. A dialogue line shows while [talking]; a teleport starts as
 * [teleported] says, none when it is null; what the trip [said] is logged in order with the walks. A question offers
 * [options]; a ship's crew is found while [crewNear]; an entrance reads as [entrances] words it, none when absent. At a
 * bank stop the next of [atBanks] says what to do, none queued going on without the bank; a booth is found while
 * [boothFound] and opens the bank while [boothOpens]; a withdrawal hands out [withdrawn], all asked for when null.
 */
class FakeTraveller(var position: Position, private val log: MutableList<String> = mutableListOf()) : Traveller {
    var busy = false
    var choosing = false
    var usable = true
    var noWay = false
    val standing = mutableSetOf<Pair<Int, Position>>()
    val entrances = mutableMapOf<Pair<Int, Position>, EntrancePassage>()
    var talking = false
    var talkedOn = 0
    var teleported: TeleportStarted? = null
    /** A worn piece the teleport took off and could not wear again, told as soon as it starts. */
    var notWornAgain: TeleportDestination.Jewellery? = null
    var options = emptyList<String>()
    var crewNear = true
    val talkedTo = mutableListOf<Pair<Set<Int>, Position>>()
    val said = mutableListOf<Said>()
    val teleports = mutableListOf<TeleportDefinition>()
    val plans = ArrayDeque<CompletableFuture<TripPlanning>>()
    val planned = mutableListOf<List<Position>>()
    val walks = mutableListOf<Position>()
    val used = mutableListOf<Triple<Int, Position, String?>>()
    val sides = mutableListOf<Position>()
    val chosen = mutableListOf<Int>()
    var interrupted = 0
    val stopsAsked = mutableListOf<Boolean>()
    val atBanks = ArrayDeque<CompletableFuture<AtBank>>()
    val stopsAt = mutableListOf<Pair<BankStop, Position>>()
    var bankOpen = false
    var boothFound = true
    var boothOpens = true
    val booths = mutableListOf<Position>()
    var withdrawn: Withdrawn? = null
    val withdrawals = mutableListOf<List<Item>>()
    var closed = 0

    override fun position(): Position = position

    override fun isBusy(): Boolean = busy

    override fun plan(destinations: List<Position>, stops: Boolean): CompletableFuture<TripPlanning> {
        planned += destinations
        stopsAsked += stops
        return plans.removeFirstOrNull() ?: CompletableFuture.completedFuture(if (noWay) NoTrip() else PlannedTrip(plainWalk(destinations.first())))
    }

    private fun plainWalk(to: Position) = WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, position, to, cost = 1.0)), cost = 1.0)

    override fun walk(to: Position) {
        walks += to
        log += "walk to ${to.x},${to.y}"
    }

    override fun standing(id: Int, at: Position): Boolean = id to at in standing

    override fun entrance(id: Int, at: Position): EntrancePassage? = entrances[id to at]

    override fun use(id: Int, at: Position, option: String?, from: Position): Boolean {
        used += Triple(id, at, option)
        sides += from
        return usable
    }

    override fun choosing(): Boolean = choosing

    override fun choose(option: Int) {
        chosen += option
    }

    override fun options(): List<String> = options

    override fun talkTo(ids: Set<Int>, near: Position): Boolean {
        talkedTo += ids to near
        return crewNear
    }

    override fun talking(): Boolean = talking

    override fun talkOn() {
        talkedOn++
    }

    override fun teleport(
        teleport: TeleportDefinition,
        starting: (TeleportStarted) -> Unit,
        notWornAgain: (TeleportDestination.Jewellery) -> Unit,
    ): TeleportStarted? {
        teleports += teleport
        teleported?.let(starting)
        this.notWornAgain?.let(notWornAgain)
        return teleported
    }

    override fun say(said: Said) {
        this.said += said
        log += "say $said"
    }

    override fun interrupt() {
        interrupted++
    }

    override fun atBank(stop: BankStop, destination: Position): CompletableFuture<AtBank> {
        stopsAt += stop to destination
        return atBanks.removeFirstOrNull() ?: CompletableFuture.completedFuture(AtBank.Skip(onward = null))
    }

    override fun bankOpen(): Boolean = bankOpen

    override fun openBank(booth: Position): Boolean {
        booths += booth
        if (boothFound && boothOpens) bankOpen = true
        return boothFound
    }

    override fun withdraw(items: List<Item>): Withdrawn {
        withdrawals += items
        log += "withdraw ${items.joinToString { "${it.amount} x ${it.id}" }}"
        return withdrawn ?: Withdrawn(items.associate { it.id to it.amount })
    }

    override fun closeBank() {
        closed++
        bankOpen = false
        log += "close the bank"
    }
}
