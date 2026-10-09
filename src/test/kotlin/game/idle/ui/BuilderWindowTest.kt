package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.flow.FakeStepType
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowResolver
import game.idle.flow.StepTypes
import game.idle.flow.option.FakeNames
import game.idle.idleState
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderWindowTest {

    private val resolver = FlowResolver(StepTypes(listOf(FakeStepType("chop"), FakeStepType("drop"))))
    private val window = BuilderWindow(BuilderOverview(resolver, FakeNames(), ClientFont(IntArray(256) { 5 })), slots = 4)
    private val idleUi = IdleUi({ it.kind }, window)
    private val autopilot = Autopilot<LunaAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val ui = LunaBuilderUi(BuilderScreen(autopilot, resolver, slots = 4), window, idleUi)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(vararg steps: game.idle.flow.StepSettings): Player =
        TestWorld.login("builder", Position(3200, 3200)).also { it.idleState = IdleState(steps = steps.toList()) }

    private fun opened(vararg steps: game.idle.flow.StepSettings): Player = login(*steps).also { window.open(it) }

    private fun types(player: Player): List<String> = TestWorld.messages(player).map { it.type }

    private fun fields(player: Player, type: String): List<Map<String, Any>> = TestWorld.messages(player).filter { it.type == type }.map { it.fields }

    /** The last text sent per widget id. */
    private fun texts(player: Player): Map<Int, String> =
        fields(player, "WidgetTextMessageWriter").associate { it.getValue("id") as Int to it.getValue("text").toString() }

    /** Whether each widget id was last sent hidden. */
    private fun hidden(player: Player): Map<Int, Any> =
        fields(player, "WidgetVisibilityMessageWriter").associate { it.getValue("id") as Int to it.getValue("hiddenUntilHovered") }

    @Test
    fun `opening tells the client the slot count first`() {
        val player = opened()

        assertEquals(mapOf("slots" to 4), fields(player, "BuilderSlotsMessageWriter").single())
        assertTrue(types(player).indexOf("BuilderSlotsMessageWriter") < types(player).indexOf("InterfaceMessageWriter"))
    }

    @Test
    fun `the window fills its widgets before it shows`() {
        val player = opened()

        assertEquals("InterfaceMessageWriter", types(player).last())
        assertEquals(BuilderWidgets.ROOT, fields(player, "InterfaceMessageWriter").last()["id"])
    }

    @Test
    fun `it opens on the overview`() {
        val hidden = hidden(opened())

        assertEquals(listOf<Any>(false, true), listOf(hidden.getValue(BuilderWidgets.OVERVIEW), hidden.getValue(BuilderWidgets.KINDS)))
    }

    @Test
    fun `the kind picker is filled with the window`() {
        assertEquals("What should step 1 do?", texts(opened())[BuilderWidgets.KINDS_TITLE])
    }

    @Test
    fun `the open window follows the idle state`() {
        val player = opened()

        idleUi.refresh(player, IdleState(steps = listOf(step("chop"))))

        assertEquals("Chop label", texts(player)[BuilderWidgets.slotKind(0)])
    }

    @Test
    fun `a closed window is left alone`() {
        val player = login()

        idleUi.refresh(player, IdleState(steps = listOf(step("chop"))))

        assertFalse(BuilderWidgets.slotKind(0) in texts(player))
    }

    @Test
    fun `the first free slot shows the kind picker`() {
        val player = opened()

        ui.click(player, BuilderWidgets.slotFace(0))

        assertEquals(true, hidden(player)[BuilderWidgets.OVERVIEW])
    }

    @Test
    fun `close closes the window`() {
        val player = opened()

        ui.click(player, BuilderWidgets.CLOSE)

        assertFalse(window.isOpen(player))
    }

    @Test
    fun `an answer's message goes to the chat box`() {
        val player = opened()

        ui.click(player, BuilderWidgets.RUN)

        assertEquals(listOf("Autopilot: the flow is empty. Add a step first."), TestWorld.chatbox(player))
    }

    @Test
    fun `a click that does nothing sends nothing`() {
        val player = opened()
        val sent = TestWorld.messages(player).size

        ui.click(player, BuilderWidgets.STATUS)

        assertEquals(sent, TestWorld.messages(player).size)
    }

    @Test
    fun `a drag moves the step and shows the slots again`() {
        val player = opened(step("chop"), step("drop"))

        ui.arrange(player, from = 0, to = 1)

        assertEquals(listOf(step("drop"), step("chop")), player.idleState.steps)
        assertEquals("Chop label", texts(player)[BuilderWidgets.slotKind(1)])
    }

    @Test
    fun `a page asked for while the window is closed only speaks`() {
        val player = login(step("chop"))

        ui.click(player, BuilderWidgets.kindFace(1))

        assertEquals(listOf("Autopilot: a new drop step: its configure screen comes in S06b."), TestWorld.chatbox(player))
        assertFalse(BuilderWidgets.OVERVIEW in hidden(player))
    }
}
