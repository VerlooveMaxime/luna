package game.idle.autopilot.bank

import game.idle.location.Bank
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BankStepTest {

    private val boothId = 2213
    private val near = Bank("near", "Near bank", Tile(3200, 3200))
    private val far = Bank("far", "Far bank", Tile(3260, 3200))
    private val runTile = Tile(3100, 3100)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private val here = Position(3200, 3200)

    // Across a fence along y = 3202 (x 3180 to 3220): 5 tiles away in a straight line, about 40 on foot.
    private val acrossFence = Bank("across", "Bank across the fence", Tile(3200, 3205))
    private val fence = { tile: Position -> tile.y == 3202 && tile.x in 3180..3220 }

    // On this side of the fence: 10 tiles away both ways.
    private val thisSide = Bank("this side", "Bank on this side", Tile(3200, 3190))

    private val usable = mapOf(acrossFence to listOf(Position(3200, 3204)), thisSide to listOf(Position(3200, 3191)))

    @Test
    fun `the nearest bank is the one a walk reaches first, not the nearest in a straight line`() {
        val nearest = BankStep(listOf(acrossFence, thisSide)).nearest(here, FakeBankTerrain(usable, fence))

        assertEquals(thisSide, nearest)
    }

    @Test
    fun `with no bank within walking reach the nearest in a straight line is taken`() {
        val walledIn = FakeBankTerrain(usable) { true }

        val nearest = BankStep(listOf(thisSide, acrossFence)).nearest(here, walledIn)

        assertEquals(acrossFence, nearest)
    }

    @Test
    fun `a lone bank on the player's floor is taken without a search`() {
        val terrain = FakeBankTerrain(usable, fence)

        BankStep(listOf(acrossFence, near.copy(booth = Tile(3200, 3200, 1)))).nearest(here, terrain)

        assertEquals(0, terrain.stepsAsked)
    }

    @Test
    fun `with no bank on the player's floor there is none to take`() {
        assertNull(BankStep(listOf(acrossFence, thisSide)).nearest(Position(3200, 3200, 1), FakeBankTerrain(usable)))
    }

    @Test
    fun `the step heads for the booth of the bank nearest to the player`() {
        val player = TestWorld.login("banker", Position(3195, 3200))
        TestWorld.place(boothId, near.booth.toPosition())
        TestWorld.place(boothId, far.booth.toPosition())
        player.inventory.add(Item(1511))

        BankStep(listOf(far, near)).activity(player, runTile).act()

        assertEquals(Position(3199, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `with no bank on the player's floor the step says so`() {
        val player = TestWorld.login("banker", Position(3195, 3200, 1))

        BankStep(listOf(near)).activity(player, runTile).act()

        assertEquals(listOf("Autopilot: there is no bank booth this step can use on this floor."), TestWorld.chatbox(player))
    }
}
