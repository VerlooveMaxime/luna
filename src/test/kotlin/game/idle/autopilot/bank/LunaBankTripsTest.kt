package game.idle.autopilot.bank

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.StraightLineWalkEstimator
import game.idle.autopilot.travel.FakeTraveller
import game.idle.location.Bank
import game.idle.location.Tile
import game.idle.movement.TravelWeb
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LunaBankTripsTest {

    private val boothId = 2213
    private val boothTile = Position(3200, 3200)
    private val bank = Bank("near", "Near bank", Tile(3200, 3200))
    private val traveller = FakeTraveller(Position(3190, 3200))

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** A web that knows a bank whose booth is on [booth], its stand south of it. */
    private fun webKnowing(booth: Position): TravelWeb {
        val node = WebWalkNode("near", Position(3195, 3200), NodeKind.WAYPOINT)
        val known = BankDefinition("near", booth, Position(booth.x, booth.y - 1), listOf(BankLink(node.id, 3)))
        return TravelWeb(WebWalkGraph(listOf(node), emptyList(), emptyList(), emptyList(), listOf(known)), { false }, { StraightLineWalkEstimator })
    }

    private fun trips(web: TravelWeb?) = LunaBankTrips(TestWorld.world, traveller, web)

    @Test
    fun `a bank whose booth is gone is used from nowhere`() {
        assertEquals(emptyList<Position>(), trips(web = null).usableFrom(bank))
    }

    @Test
    fun `a bank the web knows is used from its stand`() {
        TestWorld.place(boothId, boothTile)
        assertEquals(listOf(Position(3200, 3199)), trips(webKnowing(boothTile)).usableFrom(bank))
    }

    @Test
    fun `a bank the web does not know is used from the tiles its booth is reached from`() {
        TestWorld.place(boothId, boothTile)
        assertEquals(Position(3199, 3200), trips(webKnowing(Position(3260, 3200))).usableFrom(bank).first())
    }

    @Test
    fun `without the web a bank is used from the tiles its booth is reached from`() {
        TestWorld.place(boothId, boothTile)
        assertEquals(Position(3199, 3200), trips(web = null).usableFrom(bank).first())
    }

    @Test
    fun `the trip to the banks is the traveller's`() {
        val trip = trips(web = null).plan(listOf(Position(3199, 3200)))
        assertEquals(listOf(listOf(Position(3199, 3200))) to Position(3199, 3200), traveller.planned to trip?.destination)
    }
}
