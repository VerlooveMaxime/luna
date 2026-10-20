package game.idle.autopilot.travel

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.BankItems
import api.bot.webwalk.plan.BotCapabilities
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.plan.Withdrawal
import api.bot.webwalk.walk.BankStandPlan
import api.bot.webwalk.walk.BankView
import api.bot.webwalk.walk.BankWithdrawal
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.LunaTeleports
import game.idle.autopilot.bank.boothOn
import game.idle.flow.EntrancePassage
import game.idle.flow.LongWayCause
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
import game.idle.movement.TravelWeb
import game.idle.movement.TravelWebs
import game.idle.movement.TripMeans
import game.idle.movement.shownFlag
import game.item.degradable.jewellery.TeleportJewellery
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ContinueDialogueEvent
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.varp.PersistentVarp
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * [Traveller] for a logged-in player. Trips are planned on [web] with what [allowance] lets the planner spend, less what
 * [reserved] holds back for the flow's teleport reflexes, by [planning]: on the game thread unless told otherwise, since
 * a trip across the map takes 10 to 45 ms to plan (measured 2026-10-17), once per walk, and every decision then sees
 * its plan. With bank stops allowed, what the bank holds of coins, runes and jewellery counts too, and upstream's
 * `TripPlanner` takes the trip by the nearest bank when that is quicker (Maxime, 2026-10-20). While the rules or the
 * bag's room keep something from the trip, a second plan with everything allowed and room in the bag says why a quicker
 * trip was not taken (the long way round, Q6). Jewellery counts as [LunaTeleports]
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

    /** Whether this trip opened the bank, which is then its own window: a bank the player opened holds the trip as before. */
    private var banking = false

    /**
     * A dialogue is the trip's own window, which it answers: a climb's up-or-down question, the toll guard's lines; and
     * the bank a stop opened. A click on arrival holds the trip.
     */
    override fun isBusy(): Boolean =
        if (fleeing) LunaClicks.clicking(player) else LunaClicks.busyBesides(player, ownWindowOpen = dialogue() != null || (banking && player.bank.isOpen))

    override fun plan(destinations: List<Position>, stops: Boolean): CompletableFuture<TripPlanning> {
        val start = player.position
        val web = web ?: return CompletableFuture.completedFuture(plainWalk(start, destinations)?.let(::PlannedTrip) ?: NoTrip())
        val means = means(web, if (stops) BankItems.of(player) else null)
        val allowed = means.under(allowance, roomy = false)
        val everything = means.under(TravelAllowance.ALL, roomy = true).takeUnless { it.sameAs(allowed) }
        return CompletableFuture.supplyAsync({ trip(web, start, destinations, allowed, everything) }, planning)
    }

    /**
     * The trip on [allowed], to the destination a straight trip finds nearest when there are several; with [everything]
     * given, why a quicker one on it was not taken.
     */
    private fun trip(web: TravelWeb, start: Position, destinations: List<Position>, allowed: TripMeans, everything: TripMeans?): TripPlanning {
        val end = destinations.singleOrNull() ?: nearest(web, start, destinations, listOfNotNull(allowed, everything)) ?: return NoTrip()
        return TripPlans.of(web.trip(start, end, allowed), everything?.let { web.trip(start, end, it) }, allowance)
    }

    private fun nearest(web: TravelWeb, start: Position, destinations: List<Position>, means: List<TripMeans>): Position? =
        means.firstNotNullOfOrNull { web.plan(start, destinations, it.carried) }?.let { it.destination ?: start }

    override fun atBank(stop: BankStop, destination: Position): CompletableFuture<AtBank> {
        val web = web ?: return CompletableFuture.completedFuture(AtBank.Skip(onward = null))
        val banked = BankItems.of(player)
        val means = means(web, banked).under(allowance, roomy = false)
        val short = stop.items.filter { banked.amount(it.id) < it.amount }.map { it.id }
        // No tier: the bank holds nothing more the trip may spend now.
        val tier = means.tiers[stop.access] ?: return CompletableFuture.completedFuture(AtBank.Skip(onward = null, tooFew(short, onward = null)))
        val stand = player.position
        return CompletableFuture.supplyAsync({ atBank(web.fromBank(stand, destination, tier, means), tier, means.view, short) }, planning)
    }

    /**
     * Upstream's rule at the bank: no trip on from it, or one that needs nothing from it, or one whose withdrawal does not
     * fit [view], goes on without the bank, saying when what it came for is gone. What is taken is exactly what the trip
     * on spends, without upstream's spare (Maxime, 2026-10-20); worn jewellery to rub keeps a slot free, as upstream's.
     */
    private fun atBank(onward: BankStandPlan?, tier: CapabilitySnapshot, view: BankView, short: List<Int>): AtBank {
        val required = onward?.withdrawal?.required ?: return AtBank.Skip(onward?.plan, tooFew(short, onward))
        val keepFree = if (onward.needs.spend.charges.keys.any(view.wornJewellery::contains)) 1 else 0
        val items = BankWithdrawal.choose(view, Withdrawal(required, required), keepFree) ?: return AtBank.Skip(onward = null, LongWayCause.NoRoom)
        val (takenFor, ordered) = TripPlans.takenFor(onward.plan, tier.teleportSpends, items)
        return AtBank.Take(ordered, takenFor, onward.plan)
    }

    override fun bankOpen(): Boolean = player.bank.isOpen

    /**
     * The first of what the bank now holds [short] of that the trip [onward] no longer spends, any of them with no trip
     * on at all: gone from the bank, rather than carried meanwhile.
     */
    private fun tooFew(short: List<Int>, onward: BankStandPlan?): LongWayCause.TooFew? =
        short.firstOrNull { id -> onward == null || !spends(onward.needs.spend, id) }?.let(LongWayCause::TooFew)

    private fun spends(spend: Spend, id: Int): Boolean =
        id in spend.items || TeleportJewellery.entries.any { id in it.items && it.name in spend.charges }

    /** Luna's booths open the bank on "Use-quickly", without the banker's dialogue. */
    override fun openBank(booth: Position): Boolean {
        val found = boothOn(player.world, booth) ?: return false
        banking = LunaClicks.use(player, found.id, booth, USE_QUICKLY, player.position)
        return banking
    }

    /**
     * Each item withdrawn as the bank step's banker withdraws it, the bag counted before and after, since the bank only
     * checks that it left; stops at the first short, upstream's rule: no room for it, or the bank has none left.
     */
    override fun withdraw(items: List<Item>): Withdrawn {
        player.varpManager.setAndSendValue(PersistentVarp.WITHDRAW_AS_NOTE, 0)
        val arrived = LinkedHashMap<Int, Int>()
        for (item in items) {
            val before = player.inventory.computeAmountForId(item.id)
            val slot = player.bank.computeIndexForId(item.id)
            if (slot >= 0) player.bank.withdraw(slot, item.amount)
            val got = player.inventory.computeAmountForId(item.id) - before
            if (got > 0) arrived[item.id] = got
            if (got < item.amount) {
                val short = if (player.bank.computeAmountForId(item.id) > 0) LongWayCause.NoRoom else LongWayCause.TooFew(item.id)
                return Withdrawn(arrived, short)
            }
        }
        return Withdrawn(arrived)
    }

    override fun closeBank() {
        banking = false
        player.overlays.closeWindows(false)
    }

    private fun means(web: TravelWeb, banked: BankItems?): PlayerMeans {
        val shown = web.formObjects.filter(ObjectForms.SERVER::showsOption).map(::shownFlag).toSet()
        return PlayerMeans(player, web.graph.teleports, shown, reserved, banked)
    }

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

        const val USE_QUICKLY = "Use-quickly"
    }
}

/**
 * What [player] carries and banks of what trips spend, read on the game thread once a plan, as trips may count on it
 * under any Travel rules: jewellery as [LunaTeleports] rubs it, the bank's ([banked], null for a trip that may not stop)
 * in upstream's tiers, coins alone or with runes and jewellery; never what [reserved] keeps for the flow's teleport
 * reflexes.
 */
private class PlayerMeans(player: Player, private val teleports: List<TeleportDefinition>, flags: Set<String>, private val reserved: Spend, banked: BankItems?) {

    private val carried = charged(BotCapabilities.snapshot(player, teleports, flags), LunaTeleports.charges(player))

    private val banks: Map<BankAccess, CapabilitySnapshot> =
        if (banked == null || banked.items.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                BankAccess.ALL to charged(BotCapabilities.snapshot(player, teleports, flags, BankAccess.ALL, banked), summed(LunaTeleports.charges(player), bankCharges(banked))),
                BankAccess.COINS to charged(BotCapabilities.snapshot(player, teleports, flags, BankAccess.COINS, banked), LunaTeleports.charges(player)),
            )
        }

    private val view = bankView(player, banked ?: BankItems.EMPTY)

    private val capacity = player.inventory.capacity()

    /**
     * The means [allowance] lets a trip count on: the bank's tiers only with bank stops, coins alone when only tolls and
     * fares are on, and only those that count on more than is carried, so a trip plans no more than it must; [roomy] as
     * if the bag had room for whatever the bank gives.
     */
    fun under(allowance: TravelAllowance, roomy: Boolean): TripMeans {
        val straight = allowance.restrict(carried, reserved, teleports)
        val tiers = if (!allowance.bank) emptyMap() else banks.filterKeys { access -> if (access == BankAccess.ALL) allowance.runes || allowance.charges else allowance.coins }
        return TripMeans(
            straight,
            tiers.mapValues { (_, tier) -> allowance.restrict(tier, reserved, teleports) }.filterValues { !TripMeans.alike(it, straight) },
            if (roomy) view.copy(freeSlots = capacity) else view,
        )
    }

    private fun charged(snapshot: CapabilitySnapshot, charges: Map<String, Int>): CapabilitySnapshot =
        CapabilitySnapshot(snapshot.skillLevels, snapshot.items, snapshot.flags, snapshot.wildernessLevel, snapshot.usableTeleports, charges, snapshot.teleportSpends)

    private fun summed(one: Map<String, Int>, other: Map<String, Int>): Map<String, Int> =
        (one.keys + other.keys).associateWith { (one[it] ?: 0) + (other[it] ?: 0) }
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

/**
 * What [player] has in the bag and in [banked] as upstream's withdrawal reads it (its own copy reads a `Bot`): the
 * bag's amounts and free slots, the bank's, which of those stack, and the jewellery worn, which keeps a slot free.
 */
internal fun bankView(player: Player, banked: BankItems): BankView =
    BankView(
        inventory = player.inventory.filterNotNull().groupingBy { it.id }.fold(0) { total, item -> total + item.amount },
        freeSlots = player.inventory.computeRemainingSize(),
        bank = banked.amounts,
        stackable = banked.amounts.keys.filter { ItemDefinition.ALL.retrieve(it).isStackable }.toSet(),
        wornJewellery = TeleportJewellery.entries.filter { jewellery -> jewellery.items.any(player.equipment::contains) }.map { it.name }.toSet(),
    )

/** The charges of the teleport jewellery in [banked], by the jewellery's name, as the planner counts carried ones. */
internal fun bankCharges(banked: BankItems): Map<String, Int> =
    TeleportJewellery.entries.associate { jewellery ->
        jewellery.name to jewellery.items.sumOf { banked.amount(it).toLong() * jewellery.charges(it) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }.filterValues { it > 0 }
