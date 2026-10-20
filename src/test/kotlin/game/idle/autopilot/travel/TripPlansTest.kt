package game.idle.autopilot.travel

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.CapabilitySnapshot.Companion.COINS
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.Spend
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.TripPlan
import game.content.sailing.ShipRoute
import game.idle.flow.LongWayCause
import game.idle.flow.TakenFor
import game.idle.flow.TeleportDestination
import game.idle.movement.TravelAllowance
import game.idle.movement.TravelChoice
import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TripPlansTest {

    private val here = Position(3200, 3200)
    private val there = Position(3300, 3200)
    private val stand = Position(3185, 3436)
    private val bank = BankDefinition("varrock_0", Position(3185, 3437), stand)
    private val law = 563
    private val air = 556
    private val fire = 554
    private val glory = 1712

    private val varrock = TeleportDefinition("spell_varrock", TeleportKind.SPELL, "VARROCK", option = null, Position(3212, 3424), "spell_varrock", cost = 5)
    private val edgeville = TeleportDefinition("glory_edgeville", TeleportKind.JEWELLERY, "AMULET_OF_GLORY", option = 1, Position(3087, 3496), "glory_edgeville", cost = 5)
    private val home = TeleportDefinition("home", TeleportKind.HOME, key = null, option = null, Position(3222, 3218), "home", cost = 5)

    private val spends = mapOf(
        varrock.id to Spend(items = mapOf(law to 1, air to 3, fire to 1)),
        edgeville.id to Spend(charges = mapOf(TeleportJewellery.AMULET_OF_GLORY.name to 1)),
        home.id to Spend.NONE,
    )

    private fun walk(from: Position = here, to: Position = there) = PlanLeg(EdgeType.WALK, from, to, cost = 1.0)

    private fun teleport(definition: TeleportDefinition, type: EdgeType = EdgeType.TELEPORT) = PlanLeg(type, here, definition.destination, cost = 5.0, teleport = definition)

    private fun toll() = PlanLeg(EdgeType.CROSSING, here, there, cost = 8.0, requirements = Requirements(coins = 10))

    private fun ship(key: String = ShipRoute.PORT_SARIM_TO_KARAMJA.name) =
        PlanLeg(EdgeType.SHIP, here, there, cost = 40.0, requirements = Requirements(coins = 30), action = EdgeAction(key = key))

    private fun plan(vararg legs: PlanLeg, cost: Double = 10.0) = WebWalkPlan(legs.toList(), cost)

    private fun direct(vararg legs: PlanLeg, cost: Double = 10.0) = TripPlan.Direct(plan(*legs, cost = cost))

    private fun viaBank(withdrawal: List<Item> = listOf(Item(law, 1)), cost: Double = 6.0, fromBank: WebWalkPlan = plan(teleport(varrock))) =
        TripPlan.ViaBank(BankAccess.ALL, bank, plan(walk(here, stand), cost = 2.0), fromBank, withdrawal, cost, comparedWith = null)

    private val walking = direct(walk())

    @Test
    fun `no trip, with nothing quicker looked at, names no choice`() {
        assertEquals(NoTrip(), TripPlans.of(TripPlan.NoTrip("none"), null, TravelAllowance.NONE))
    }

    @Test
    fun `no trip names the first choice the trip everything allowed takes needs`() {
        assertEquals(NoTrip(TravelChoice.COINS), TripPlans.of(TripPlan.NoTrip("none"), direct(teleport(varrock), toll()), TravelAllowance.NONE))
    }

    @Test
    fun `no trip names bank stops when only a trip by the bank goes there`() {
        assertEquals(NoTrip(TravelChoice.BANK), TripPlans.of(TripPlan.NoTrip("none"), viaBank(), TravelAllowance(runes = true)))
    }

    @Test
    fun `no trip either way names no choice`() {
        assertEquals(NoTrip(), TripPlans.of(TripPlan.NoTrip("none"), TripPlan.NoTrip("none"), TravelAllowance.NONE))
    }

    @Test
    fun `the straight trip is taken as planned`() {
        assertEquals(PlannedTrip(walking.plan), TripPlans.of(walking, null, TravelAllowance.NONE))
    }

    @Test
    fun `no trip everything allowed says nothing`() {
        assertEquals(PlannedTrip(walking.plan), TripPlans.of(walking, TripPlan.NoTrip("none"), TravelAllowance.NONE))
    }

    @Test
    fun `a quicker trip that is not quicker says nothing`() {
        assertEquals(PlannedTrip(walking.plan), TripPlans.of(walking, direct(teleport(varrock), cost = 10.0), TravelAllowance.NONE))
    }

    @Test
    fun `a quicker trip needing choices that are off names each, in the tab's order`() {
        val quicker = direct(teleport(varrock), toll(), cost = 4.0)

        assertEquals(
            listOf(LongWayCause.Off(TravelChoice.COINS), LongWayCause.Off(TravelChoice.RUNES)),
            (TripPlans.of(walking, quicker, TravelAllowance.NONE) as PlannedTrip).longWay,
        )
    }

    @Test
    fun `a quicker trip by the bank with bank stops off names them`() {
        assertEquals(listOf(LongWayCause.Off(TravelChoice.BANK)), (TripPlans.of(walking, viaBank(), TravelAllowance(runes = true)) as PlannedTrip).longWay)
    }

    @Test
    fun `a quicker trip by the bank that everything allows was left for want of room`() {
        assertEquals(listOf(LongWayCause.NoRoom), (TripPlans.of(walking, viaBank(), TravelAllowance.ALL) as PlannedTrip).longWay)
    }

    @Test
    fun `a straight trip a little quicker on the same choices says nothing`() {
        assertEquals(emptyList<LongWayCause>(), (TripPlans.of(walking, direct(walk(), cost = 9.0), TravelAllowance.NONE) as PlannedTrip).longWay)
    }

    @Test
    fun `a trip by the bank goes to its stand and stops there for what it takes`() {
        val chosen = viaBank()

        assertEquals(
            PlannedTrip(chosen.toBank, stop = BankStop(bank, BankAccess.ALL, chosen.withdrawal), end = varrock.destination),
            TripPlans.of(chosen, null, TravelAllowance.ALL),
        )
    }

    @Test
    fun `a trip by the bank that ends at the bank ends at its stand`() {
        assertEquals(stand, (TripPlans.of(viaBank(fromBank = plan()), null, TravelAllowance.ALL) as PlannedTrip).end)
    }

    @Test
    fun `a quicker stop taking more than the bag had room for was left for want of room`() {
        val quicker = viaBank(listOf(Item(law, 1), Item(COINS, 30)), cost = 4.0)

        assertEquals(listOf(LongWayCause.NoRoom), (TripPlans.of(viaBank(), quicker, TravelAllowance.ALL) as PlannedTrip).longWay)
    }

    @Test
    fun `a quicker stop taking the same says nothing`() {
        assertEquals(emptyList<LongWayCause>(), (TripPlans.of(viaBank(), viaBank(cost = 4.0), TravelAllowance.ALL) as PlannedTrip).longWay)
    }

    @Test
    fun `runes taken for one spell are for that teleport, in the order the spell lists them`() {
        val taken = listOf(Item(law, 1), Item(fire, 1), Item(air, 3))

        assertEquals(
            TakenFor.Teleport(TeleportDestination.Spell(TeleportSpell.VARROCK)) to listOf(Item(air, 3), Item(fire, 1), Item(law, 1)),
            TripPlans.takenFor(plan(walk(), teleport(varrock)), spends, taken),
        )
    }

    @Test
    fun `jewellery taken for its teleport is for that teleport`() {
        val taken = listOf(Item(glory, 1))

        assertEquals(
            TakenFor.Teleport(TeleportDestination.Jewellery(TeleportJewellery.AMULET_OF_GLORY, 0)) to taken,
            TripPlans.takenFor(plan(teleport(edgeville)), spends, taken),
        )
    }

    @Test
    fun `coins taken for the toll gate are for the toll`() {
        assertEquals(TakenFor.Toll to listOf(Item(COINS, 10)), TripPlans.takenFor(plan(toll()), spends, listOf(Item(COINS, 10))))
    }

    @Test
    fun `coins taken for a ship are for its fare to where it sails`() {
        assertEquals(TakenFor.Fare("Karamja"), TripPlans.takenFor(plan(ship()), spends, listOf(Item(COINS, 30))).first)
    }

    @Test
    fun `a ship Luna does not know is no purpose`() {
        assertEquals(TakenFor.Trip, TripPlans.takenFor(plan(ship("NOWHERE")), spends, listOf(Item(COINS, 30))).first)
    }

    @Test
    fun `a ship leg naming no route is no purpose`() {
        val ship = PlanLeg(EdgeType.SHIP, here, there, cost = 40.0, requirements = Requirements(coins = 30))

        assertEquals(TakenFor.Trip, TripPlans.takenFor(plan(ship), spends, listOf(Item(COINS, 30))).first)
    }

    @Test
    fun `a spell cast with what is carried is not what the coins are for`() {
        assertEquals(TakenFor.Toll, TripPlans.takenFor(plan(teleport(varrock), toll()), spends, listOf(Item(COINS, 10))).first)
    }

    @Test
    fun `a toll paid with coins carried is not what the runes are for`() {
        assertEquals(TakenFor.Teleport(TeleportDestination.Spell(TeleportSpell.VARROCK)), TripPlans.takenFor(plan(toll(), teleport(varrock)), spends, listOf(Item(law, 1))).first)
    }

    @Test
    fun `two spends taken for are the whole trip's`() {
        val taken = listOf(Item(COINS, 10), Item(law, 1))

        assertEquals(TakenFor.Trip to taken, TripPlans.takenFor(plan(toll(), teleport(varrock)), spends, taken))
    }

    @Test
    fun `the same teleport twice is one purpose`() {
        assertEquals(TakenFor.Teleport(TeleportDestination.Spell(TeleportSpell.VARROCK)), TripPlans.takenFor(plan(teleport(varrock), teleport(varrock)), spends, listOf(Item(law, 2))).first)
    }

    @Test
    fun `a teleport that spends nothing known, or nothing a reflex offers, is no purpose`() {
        val unknown = varrock.copy(id = "spell_unknown")

        assertEquals(TakenFor.Trip, TripPlans.takenFor(plan(teleport(unknown), teleport(home)), spends + (home.id to Spend(items = mapOf(law to 1))), listOf(Item(law, 1))).first)
    }

    @Test
    fun `a leg carrying a teleport but not one is no purpose`() {
        assertEquals(TakenFor.Trip, TripPlans.takenFor(plan(teleport(varrock, EdgeType.WALK), PlanLeg(EdgeType.TELEPORT, here, there, cost = 1.0)), spends, listOf(Item(law, 1))).first)
    }
}
