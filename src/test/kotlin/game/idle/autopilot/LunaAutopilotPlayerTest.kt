package game.idle.autopilot

import api.predef.woodcutting
import game.idle.IdleState
import game.idle.autopilot.bank.BankActivity
import game.idle.autopilot.drop.DropActivity
import game.idle.autopilot.woodcutting.ChopAction
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.autopilot.woodcutting.WoodcuttingSpot
import game.idle.flow.ResolvedStep
import game.idle.idleState
import game.idle.location.Area
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
    private val area = Area(Tile(3200, 3200), radius = 10)
    private val location = Location(
        id = "test_grove",
        name = "Test grove",
        trees = mapOf("normal" to area),
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

    private fun overlayTexts(autopilot: LunaAutopilotPlayer): List<String> =
        TestWorld.messages(autopilot.player)
            .filter { it.type == "StatusOverlayMessageWriter" }
            .map { it.fields.getValue("text").toString() }

    @Test
    fun `starting a flow shows it on the status overlay`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState(flow = listOf("chop oak @test_grove", "loop")).started()

        assertEquals(listOf("@gre@Autopilot@whi@ step 1/2|@yel@chop oak @test_grove"), overlayTexts(autopilot))
    }

    @Test
    fun `stopping a flow clears the status overlay`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState(flow = listOf("chop oak @test_grove")).stopped()

        assertEquals(listOf(""), overlayTexts(autopilot))
    }

    @Test
    fun `saving a step refreshes the status overlay`() {
        val autopilot = autopilotPlayer()
        autopilot.idleState = IdleState(flow = listOf("chop oak @test_grove", "loop")).started()

        autopilot.saveStep(1)

        assertEquals("@gre@Autopilot@whi@ step 2/2|@yel@loop", overlayTexts(autopilot).last())
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val autopilot = autopilotPlayer()

        autopilot.tell("Chopping at the test grove.")

        assertEquals(listOf("Chopping at the test grove."), TestWorld.chatbox(autopilot.player))
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
        val spot = WoodcuttingSpot(location, Tree.NORMAL, area)
        val step = ResolvedStep.Chop(spot, ChopAction(setOf(Tree.NORMAL)))

        assertInstanceOf(WoodcuttingActivity::class.java, autopilotPlayer().chop(step))
    }

    @Test
    fun `a drop step runs as a drop activity`() {
        assertInstanceOf(DropActivity::class.java, autopilotPlayer().drop(ResolvedStep.Drop(setOf(logs))))
    }

    @Test
    fun `a bank step runs as a bank activity`() {
        val step = ResolvedStep.Bank(location, booth = Tile(3210, 3200))

        assertInstanceOf(BankActivity::class.java, autopilotPlayer().bank(step))
    }
}
