package game.idle.ui

import game.idle.IdleState
import game.idle.flow.StepSettings
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdleUiTest {

    private val running = IdleState(steps = listOf(StepSettings("chop"), StepSettings("drop")), stepIndex = 1, running = true)
    private val idleUi = IdleUi({ it.kind }, idleTab(slots = 3))

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("ui", Position(3200, 3200))

    /** The last text sent per widget id. */
    private fun texts(player: Player): Map<Int, String> =
        TestWorld.messages(player)
            .filter { it.type == "WidgetTextMessageWriter" }
            .associate { it.fields.getValue("id") as Int to it.fields.getValue("text").toString() }

    /** Message types sent after the login itself (which assigns the player its index). */
    private fun types(player: Player): List<String> =
        TestWorld.messages(player).map { it.type }.filter { it != "AssignmentMessageWriter" }

    @Test
    fun `installing the tab fills slot 7 and sends the status lines`() {
        val player = login()

        idleUi.installTab(player, IdleState())

        val tab = TestWorld.messages(player).single { it.type == "TabInterfaceMessageWriter" }
        assertEquals(FlowWidgets.TAB, tab.fields["id"])
        assertEquals("Autopilot: stopped", texts(player)[FlowWidgets.TAB_STATUS_1])
    }

    @Test
    fun `the client hears of the saved-flow slots before the tab, to build its rows`() {
        val player = login()

        idleUi.installTab(player, IdleState())

        val count = TestWorld.messages(player).single { it.type == "SlotCountMessageWriter" }
        assertEquals(mapOf("kind" to SlotCountMessageWriter.SAVED_FLOW_SLOTS, "slots" to 3), count.fields)
        assertTrue(types(player).indexOf("SlotCountMessageWriter") < types(player).indexOf("TabInterfaceMessageWriter"))
    }

    @Test
    fun `a refresh sends the overlay and the tab's widgets`() {
        val player = login()

        idleUi.refresh(player, running)

        val overlay = TestWorld.messages(player).single { it.type == "StatusOverlayMessageWriter" }
        assertEquals("@gre@Autopilot@whi@ step 2/2|@yel@drop", overlay.fields["text"])
        assertEquals("Autopilot: running step 2 of 2", texts(player)[FlowWidgets.TAB_STATUS_1])
    }

    @Test
    fun `a refresh without a builder sends nothing else`() {
        val player = login()

        idleUi.refresh(player, running)

        assertEquals(setOf("StatusOverlayMessageWriter", "WidgetTextMessageWriter", "WidgetVisibilityMessageWriter"), types(player).toSet())
    }

    @Test
    fun `the same text is sent again, since the client may have rebuilt the widget blank`() {
        val player = login()

        idleUi.refresh(player, running)
        idleUi.refresh(player, running)

        assertEquals(2, TestWorld.messages(player).count { it.type == "WidgetTextMessageWriter" && it.fields["id"] == FlowWidgets.TAB_STATUS_1 })
    }
}
