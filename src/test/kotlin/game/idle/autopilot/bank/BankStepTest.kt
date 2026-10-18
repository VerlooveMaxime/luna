package game.idle.autopilot.bank

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.autopilot.travel.NoTrip
import game.idle.autopilot.travel.PlannedTrip
import game.idle.autopilot.travel.TripPlanning
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.StepItem
import game.idle.flow.StepOutcome
import game.idle.location.Bank
import game.idle.location.Tile
import game.idle.movement.TravelChoice
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BankStepTest {

    private val boothId = 2213
    private val near = Bank("near", "Near bank", Tile(3200, 3200))
    private val far = Bank("far", "Far bank", Tile(3260, 3200))
    private val runTile = Tile(3100, 3100)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private val here = Position(3200, 3200)

    private val acrossFence = Bank("across", "Bank across the fence", Tile(3200, 3205))
    private val thisSide = Bank("this side", "Bank on this side", Tile(3200, 3190))
    private val acrossEnd = Position(3200, 3204)
    private val thisSideEnd = Position(3200, 3191)
    private val ends = mapOf(acrossFence to listOf(acrossEnd), thisSide to listOf(thisSideEnd))

    /** Trips by [trip], which picks the end the cheapest trip reaches; [planned] records what was asked. */
    private class FakeBankTrips(private val usable: Map<Bank, List<Position>>, private val trip: (List<Position>) -> TripPlanning) : BankTrips {
        val planned = mutableListOf<List<Position>>()

        override fun usableFrom(bank: Bank): List<Position> = usable[bank].orEmpty()

        override fun plan(ends: List<Position>): TripPlanning {
            planned += ends
            return trip(ends)
        }
    }

    private fun walkTo(end: Position) = PlannedTrip(WebWalkPlan(listOf(PlanLeg(EdgeType.WALK, here, end, cost = 40.0)), cost = 40.0))

    @Test
    fun `the nearest bank is the one the cheapest trip reaches, not the nearest in a straight line`() {
        val trip = walkTo(thisSideEnd)

        val nearest = BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(here, FakeBankTrips(ends) { trip })

        assertEquals(BankReach(BankTrip(thisSide, thisSideEnd, trip)), nearest)
    }

    @Test
    fun `one trip is planned to every tile the banks are used from`() {
        val trips = FakeBankTrips(ends) { walkTo(thisSideEnd) }

        BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(here, trips)

        assertEquals(listOf(listOf(acrossEnd, thisSideEnd)), trips.planned)
    }

    @Test
    fun `with no booth standing at any bank there is none to take, and no trip is planned`() {
        val trips = FakeBankTrips(emptyMap()) { walkTo(thisSideEnd) }

        val nearest = BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(here, trips)

        assertEquals(BankReach(trip = null) to 0, nearest to trips.planned.size)
    }

    @Test
    fun `with no trip to a bank there is none to take`() {
        assertEquals(BankReach(trip = null), BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(here, FakeBankTrips(ends) { NoTrip() }))
    }

    @Test
    fun `with no trip to a bank but one the flow's Travel setting would allow, the choice is named`() {
        val nearest = BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(here, FakeBankTrips(ends) { NoTrip(TravelChoice.COINS) })

        assertEquals(BankReach(trip = null, TravelChoice.COINS), nearest)
    }

    @Test
    fun `a player already standing where a booth is used from takes that bank`() {
        val standing = PlannedTrip(WebWalkPlan(emptyList(), cost = 0.0))

        val nearest = BankStep(listOf(acrossFence, thisSide), EVERYTHING).nearest(thisSideEnd, FakeBankTrips(ends) { standing })

        assertEquals(BankReach(BankTrip(thisSide, thisSideEnd, standing)), nearest)
    }

    @Test
    fun `the step heads for the booth of the bank nearest to the player`() {
        val player = TestWorld.login("banker", Position(3195, 3200))
        TestWorld.place(boothId, near.booth.toPosition())
        TestWorld.place(boothId, far.booth.toPosition())
        player.inventory.add(Item(1511))

        BankStep(listOf(far, near), EVERYTHING).work(player, runTile).act(left = null)

        assertEquals(Position(3199, 3200), player.navigator.currentTarget)
    }

    /** No stairs of the web reach floor 3 around TestWorld's map, so no trip there leaves it. */
    @Test
    fun `with no trip to a bank the step blocks`() {
        val player = TestWorld.login("banker", Position(3195, 3200, 3))
        TestWorld.place(boothId, near.booth.toPosition())
        player.inventory.add(Item(1511))
        val work = BankStep(listOf(near), EVERYTHING).work(player, runTile)

        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.BANK)), work.act(left = null))
    }

    private val withdrawing = BankStep(listOf(near), BankOrder(DepositRule.Everything, listOf(StepItem(1521), StepItem(1511, 5)), IfStuck.SKIP))

    @Test
    fun `a bank step withdraws the amount it holds of an item, null for all of it`() {
        assertEquals(listOf(5, null, null), listOf(withdrawing.amount(1511), withdrawing.amount(1521), withdrawing.amount(590)))
    }

    @Test
    fun `without some withdrawals the step keeps the others, in order`() {
        val without = withdrawing.without(setOf(1521)) as BankStep

        assertEquals(listOf(StepItem(1511, 5)), without.order.withdrawals)
        assertEquals(near, without.candidates.single())
    }

    @Test
    fun `without nothing the step is itself`() {
        assertSame(withdrawing, withdrawing.without(emptySet()))
    }
}
