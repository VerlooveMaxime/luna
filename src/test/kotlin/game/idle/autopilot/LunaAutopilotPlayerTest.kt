package game.idle.autopilot

import api.predef.woodcutting
import game.idle.IdleState
import game.idle.autopilot.bank.BankActivity
import game.idle.autopilot.woodcutting.ChopAction
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.flow.ResolvedStep
import game.idle.idleState
import game.idle.location.Location
import game.idle.location.LocationUnlock
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaAutopilotPlayerTest {

    private val logs = 1511
    private val location = Location(
        id = "test_grove",
        name = "Test grove",
        anchor = Tile(3200, 3200),
        radius = 10,
        trees = listOf("normal"),
        bank = Tile(3210, 3200),
        unlock = LocationUnlock(stage = 0),
    )

    private fun autopilotPlayer() = LunaAutopilotPlayer(TestWorld.login("autopilot", Position(3200, 3200)))

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `the username is the player's`() {
        assertEquals("autopilot", autopilotPlayer().username)
    }

    @Test
    fun `the idle state written is the player's`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState().started()

        assertTrue(autopilot.player.idleState.running)
    }

    @Test
    fun `the idle state read is the player's`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(stepIndex = 2)

        assertEquals(2, autopilot.idleState.stepIndex)
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val autopilot = autopilotPlayer()

        autopilot.tell("Chopping at the test grove.")

        assertEquals(listOf("Chopping at the test grove."), TestWorld.chatbox(autopilot.player))
    }

    @Test
    fun `the woodcutting level is the player's current one`() {
        val autopilot = autopilotPlayer()
        autopilot.player.woodcutting.level = 15

        assertEquals(15, autopilot.woodcuttingLevel())
    }

    @Test
    fun `an inventory with free slots is not full`() {
        assertFalse(autopilotPlayer().inventoryFull())
    }

    @Test
    fun `an inventory without free slots is full`() {
        val autopilot = autopilotPlayer()
        autopilot.player.inventory.add(Item(logs, 28))

        assertTrue(autopilot.inventoryFull())
    }

    @Test
    fun `owned items count both the inventory and the bank`() {
        val autopilot = autopilotPlayer()
        autopilot.player.inventory.add(Item(logs, 2))
        autopilot.player.bank.add(Item(logs, 3))

        assertEquals(5, autopilot.countOwned(logs))
    }

    @Test
    fun `saving a step keeps the rest of the idle state`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(flow = listOf("bank @test_grove"), running = true)

        autopilot.saveStep(1)

        assertEquals(IdleState(flow = listOf("bank @test_grove"), stepIndex = 1, running = true), autopilot.player.idleState)
    }

    @Test
    fun `a chop step runs as a woodcutting activity`() {
        val spot = WoodcuttingSpot(location, setOf(Tree.NORMAL))
        val step = ResolvedStep.Chop(spot, ChopAction(setOf(Tree.NORMAL), dropWhenFull = true), until = null)

        assertInstanceOf(WoodcuttingActivity::class.java, autopilotPlayer().chop(step))
    }

    @Test
    fun `a bank step runs as a bank activity`() {
        val step = ResolvedStep.Bank(location, booth = Tile(3210, 3200))

        assertInstanceOf(BankActivity::class.java, autopilotPlayer().bank(step))
    }
}
