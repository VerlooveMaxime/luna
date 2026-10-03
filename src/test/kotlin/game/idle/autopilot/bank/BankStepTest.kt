package game.idle.autopilot.bank

import game.idle.location.Bank
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BankStepTest {

    private val boothId = 2213
    private val near = Bank("near", "Near bank", Tile(3200, 3200))
    private val far = Bank("far", "Far bank", Tile(3260, 3200))
    private val runTile = Tile(3100, 3100)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

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
