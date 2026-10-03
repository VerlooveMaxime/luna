package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.IdleSteps
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.flow.FlowResolver
import game.idle.idleState
import game.idle.location.BankCatalog
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaFlowUiTest {

    private val autopilot = Autopilot<LunaAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val resolver = FlowResolver(IdleSteps(BankCatalog(emptyList())).grammar)
    private val ui = LunaFlowUi(FlowBuilder(autopilot, resolver))

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("clicker", Position(3200, 3200))

    private fun texts(player: Player): Map<Int, String> =
        TestWorld.messages(player)
            .filter { it.type == "WidgetTextMessageWriter" }
            .associate { it.fields.getValue("id") as Int to it.fields.getValue("text").toString() }

    /** Message types sent after the login itself (which assigns the player its index). */
    private fun types(player: Player): List<String> =
        TestWorld.messages(player).map { it.type }.filter { it != "AssignmentMessageWriter" }

    @Test
    fun `the tab's builder button opens the builder with its texts`() {
        val player = login()

        ui.click(player, FlowWidgets.TAB_OPEN_BUILDER)

        assertTrue(player.overlays.has(FlowBuilderInterface::class.java))
        assertTrue("InterfaceMessageWriter" in types(player))
        assertEquals("chop", texts(player)[FlowWidgets.DRAFT_KIND])
        assertEquals("No steps yet.", texts(player)[FlowWidgets.STATUS])
    }

    @Test
    fun `the close button closes the windows`() {
        val player = login()
        ui.click(player, FlowWidgets.TAB_OPEN_BUILDER)

        ui.click(player, FlowWidgets.BUILDER_CLOSE)

        assertFalse(player.overlays.has(FlowBuilderInterface::class.java))
        assertTrue("CloseWindowsMessageWriter" in types(player))
    }

    @Test
    fun `a click with the builder open refreshes its texts`() {
        val player = login()
        ui.click(player, FlowWidgets.TAB_OPEN_BUILDER)

        ui.click(player, FlowWidgets.DRAFT_KIND)

        assertEquals("walk", texts(player)[FlowWidgets.DRAFT_KIND])
        assertEquals("3200 3200", texts(player)[FlowWidgets.DRAFT_FIELDS[0]])
    }

    @Test
    fun `a tab click with the builder closed speaks in the chat box`() {
        val player = login()

        ui.click(player, FlowWidgets.TAB_RUN)

        assertEquals(listOf("The flow is empty. Add a step first."), TestWorld.chatbox(player))
    }

    @Test
    fun `a tab run starts the flow and shows it on the tab`() {
        val player = login()
        player.idleState = IdleState(flow = listOf("chop normal"))

        ui.click(player, FlowWidgets.TAB_RUN)

        assertTrue(autopilot.isRunning(LunaAutopilotPlayer(player)))
        assertEquals("Autopilot: running", texts(player)[FlowWidgets.TAB_STATUS_1])
        assertEquals(listOf("Running step 1: chop normal"), TestWorld.chatbox(player))
    }

    @Test
    fun `a silent click with the builder closed says nothing`() {
        val player = login()

        ui.click(player, FlowWidgets.DRAFT_KIND)

        assertEquals(emptyList<String>(), types(player))
    }

    @Test
    fun `an unknown widget does nothing`() {
        val player = login()

        ui.click(player, 1)

        assertEquals(emptyList<String>(), types(player))
    }

    @Test
    fun `forgetting a player starts the next visit from a fresh draft`() {
        val player = login()
        ui.click(player, FlowWidgets.DRAFT_KIND)

        ui.forget(player)
        ui.click(player, FlowWidgets.TAB_OPEN_BUILDER)

        assertEquals("chop", texts(player)[FlowWidgets.DRAFT_KIND])
    }
}
