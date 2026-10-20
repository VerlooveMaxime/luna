package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.TripPlan
import game.content.sailing.ShipRoute
import game.idle.flow.LongWayCause
import game.idle.flow.TakenFor
import game.idle.flow.TeleportDestination
import game.idle.flow.Teleports
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import game.item.degradable.jewellery.TeleportJewellery
import io.luna.game.model.item.Item

/** Our trips from upstream's choices between a straight trip and one by the bank (Maxime, 2026-10-20). */
object TripPlans {

    /**
     * The trip [chosen] under the flow's [allowance], with why the [quicker] one that everything allowed and room in the
     * bag would take was not: the Travel choices it needs that are off, else no room for what it takes from the bank.
     */
    fun of(chosen: TripPlan, quicker: TripPlan?, allowance: TravelAllowance): TripPlanning =
        when (chosen) {
            is TripPlan.NoTrip -> NoTrip(quicker?.let { off(it, allowance) }?.minOrNull())
            is TripPlan.Direct -> PlannedTrip(chosen.plan, longWay(chosen.plan.cost, emptyList(), quicker, allowance))
            is TripPlan.ViaBank -> {
                val stop = BankStop(chosen.bank, chosen.access, chosen.withdrawal)
                PlannedTrip(chosen.toBank, longWay(chosen.cost, chosen.withdrawal, quicker, allowance), stop, chosen.fromBank.destination ?: chosen.bank.stand)
            }
        }

    private fun longWay(cost: Double, taking: List<Item>, quicker: TripPlan?, allowance: TravelAllowance): List<LongWayCause> {
        val other = quicker?.takeIf { costOf(it) < cost } ?: return emptyList()
        val off = off(other, allowance)
        if (off.isNotEmpty()) return off.sorted().map(LongWayCause::Off)
        val more = (other as? TripPlan.ViaBank)?.withdrawal.orEmpty().filter { item -> taking.none { it.id == item.id } }
        return if (more.isEmpty()) emptyList() else listOf(LongWayCause.NoRoom)
    }

    private fun costOf(trip: TripPlan): Double =
        when (trip) {
            is TripPlan.NoTrip -> Double.POSITIVE_INFINITY
            is TripPlan.Direct -> trip.plan.cost
            is TripPlan.ViaBank -> trip.cost
        }

    /** The Travel choices [trip] needs that [allowance] does not give: what its legs spend by, and a stop at the bank. */
    private fun off(trip: TripPlan, allowance: TravelAllowance): Set<TravelChoice> {
        val legs = when (trip) {
            is TripPlan.NoTrip -> emptyList()
            is TripPlan.Direct -> trip.plan.legs
            is TripPlan.ViaBank -> trip.toBank.legs + trip.fromBank.legs
        }
        val stop = TravelChoice.BANK.takeIf { trip is TripPlan.ViaBank }
        return (legs.mapNotNull(TravelChoice::of) + listOfNotNull(stop)).filterNot(allowance::allows).toSet()
    }

    /**
     * What [items] taken from the bank are for on [plan], whose teleports spend what [spends] says: the one leg spending
     * any of them (a teleport, the toll, a fare), else the whole trip. A spell's runes are in the order it lists them.
     */
    fun takenFor(plan: WebWalkPlan, spends: Map<String, Spend>, items: List<Item>): Pair<TakenFor, List<Item>> {
        val taken = items.map { it.id }.toSet()
        val uses = plan.legs.mapNotNull { leg -> use(leg, spends, taken) }.distinct()
        val use = uses.singleOrNull() ?: return TakenFor.Trip to items
        val spell = (use as? TakenFor.Teleport)?.destination as? TeleportDestination.Spell ?: return use to items
        val ordered = Teleports.inSpellOrder(spell.spell.requirements, items.associate { it.id to it.amount })
        return use to ordered.map { (id, amount) -> Item(id, amount) }
    }

    private fun use(leg: PlanLeg, spends: Map<String, Spend>, taken: Set<Int>): TakenFor? {
        if (leg.requirements.coins > 0) {
            if (CapabilitySnapshot.COINS !in taken) return null
            if (leg.type != EdgeType.SHIP) return TakenFor.Toll
            return ShipRoute.entries.firstOrNull { it.name == leg.action?.key }?.let { TakenFor.Fare(it.place) }
        }
        val teleport = leg.teleport?.takeIf { leg.type == EdgeType.TELEPORT } ?: return null
        val spend = spends[teleport.id] ?: return null
        val jewellery = spend.charges.keys.mapNotNull(TeleportJewellery::named).flatMap { it.items }
        if (spend.items.keys.none(taken::contains) && jewellery.none(taken::contains)) return null
        return Teleports.of(teleport)?.let(TakenFor::Teleport)
    }
}
