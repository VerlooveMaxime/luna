package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.LunaAutopilotPlayer
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowResolver
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import game.idle.idleState
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderWindowTest {

    private val resolver = FlowResolver(CONFIGURED_TYPES)
    private val font = ClientFont(IntArray(256) { 5 })
    private val window = BuilderWindow(BuilderOverview(resolver, FakeNames(), font), BuilderConfigure(resolver, FakeNames(), font), slots = 4)
    private val idleUi = IdleUi({ it.kind }, window)
    private val autopilot = Autopilot<LunaAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val ui = LunaBuilderUi(BuilderScreen(autopilot, resolver, FakeNames(), slots = 4), window, idleUi, font)

    private val oak = step("chop", "oak")

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(vararg steps: StepSettings): Player =
        TestWorld.login("builder", Position(3200, 3200)).also { it.idleState = IdleState(steps = steps.toList()) }

    private fun opened(vararg steps: StepSettings): Player = login(*steps).also { window.open(it) }

    /** The window open on step [slot]'s configure screen. */
    private fun configuring(slot: Int, vararg steps: StepSettings): Player = opened(*steps).also { ui.click(it, BuilderWidgets.slotFace(slot)) }

    private fun types(player: Player): List<String> = TestWorld.messages(player).map { it.type }

    private fun fields(player: Player, type: String): List<Map<String, Any>> = TestWorld.messages(player).filter { it.type == type }.map { it.fields }

    /** The last text sent per widget id. */
    private fun texts(player: Player): Map<Int, String> =
        fields(player, "WidgetTextMessageWriter").associate { it.getValue("id") as Int to it.getValue("text").toString() }

    /** Whether each widget id was last sent hidden. */
    private fun hidden(player: Player): Map<Int, Any> =
        fields(player, "WidgetVisibilityMessageWriter").associate { it.getValue("id") as Int to it.getValue("hiddenUntilHovered") }

    /** The last colour sent per widget id. */
    private fun colours(player: Player): Map<Int, Any> =
        fields(player, "WidgetColourMessageWriter").associate { it.getValue("widgetId") as Int to it.getValue("rgb") }

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

        assertEquals(
            listOf<Any>(false, true, true),
            listOf(hidden.getValue(BuilderWidgets.OVERVIEW), hidden.getValue(BuilderWidgets.KINDS), hidden.getValue(BuilderWidgets.CONFIGURE)),
        )
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
    fun `a step's slot shows its configure screen`() {
        val player = configuring(0, oak)

        assertEquals(listOf<Any>(true, false), listOf(hidden(player).getValue(BuilderWidgets.OVERVIEW), hidden(player).getValue(BuilderWidgets.CONFIGURE)))
        assertEquals("Oak", texts(player)[BuilderWidgets.rowText(0)])
    }

    @Test
    fun `the configure screen keeps the step it shows`() {
        assertEquals(StepDraft(0, oak, new = false), window.draft(configuring(0, oak)))
    }

    @Test
    fun `the configure screen shows the levels as the player has them`() {
        assertEquals("Step 1: Chop label @gry@(Woodcutting 1)", texts(configuring(0, oak))[BuilderWidgets.HEADER_NAME])
    }

    @Test
    fun `the configure screen follows the idle state`() {
        val player = configuring(0, oak)

        idleUi.refresh(player, IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: you need an axe."))

        assertEquals("@red@! You need an axe.", texts(player)[BuilderWidgets.warning(0)])
    }

    @Test
    fun `back drops the step being configured`() {
        val player = configuring(0, oak)

        ui.click(player, BuilderWidgets.BACK)

        assertNull(window.draft(player))
    }

    @Test
    fun `closing the window drops the step being configured`() {
        val player = configuring(0, oak)

        ui.click(player, BuilderWidgets.CLOSE)

        assertNull(window.draft(player))
    }

    @Test
    fun `a step's slot clicked while the window is closed shows nothing`() {
        val player = login(oak)

        ui.click(player, BuilderWidgets.slotFace(0))

        assertNull(window.draft(player))
        assertFalse(BuilderWidgets.CONFIGURE in hidden(player))
    }

    @Test
    fun `save puts the new step in the flow and says so`() {
        val player = opened()
        ui.click(player, BuilderWidgets.kindFace(1))

        ui.click(player, BuilderWidgets.SAVE)

        assertEquals(listOf(step("drop")), player.idleState.steps)
        assertEquals(listOf("Autopilot: step 1 saved."), TestWorld.chatbox(player))
    }

    @Test
    fun `a configure answer's message goes to the chat box`() {
        val player = configuring(0, oak)
        autopilot.start(LunaAutopilotPlayer(player, idleUi))

        ui.click(player, BuilderWidgets.SAVE)

        assertEquals(BuilderScreen.STOP_FIRST, TestWorld.chatbox(player).last())
    }

    @Test
    fun `a search field opens the search over its target`() {
        val player = configuring(0, step("chop"))

        ui.click(player, BuilderWidgets.rowFace(0))

        assertEquals("Which tree?", fields(player, "SearchOpenMessageWriter").single()["title"])
    }

    @Test
    fun `a pick in the search sets the step being configured`() {
        val player = configuring(0, step("chop"))
        ui.click(player, BuilderWidgets.rowFace(0))

        SearchPrompts.pick(player, checkNotNull(SearchPrompts.opened(player, SearchPrompt::class.java)).serial, 0)

        assertEquals(oak, window.draft(player)?.settings)
        assertEquals("Oak", texts(player)[BuilderWidgets.rowText(0)])
    }

    @Test
    fun `a typed field opens the amount prompt and frames the field`() {
        val player = configuring(0, oak)

        ui.click(player, BuilderWidgets.rowFace(1))

        assertTrue("NumberInputMessageWriter" in types(player))
        assertEquals(WidgetColourMessageWriter.unpacked(WidgetColourMessageWriter.packed(BuilderWidgets.TYPING)), colours(player)[BuilderWidgets.rowFrame(1)])
    }

    @Test
    fun `an amount typed sets the step being configured`() {
        val player = configuring(0, oak)
        ui.click(player, BuilderWidgets.rowFace(1))

        checkNotNull(player.overlays.getOverlay(AmountInput::class.java)).input(player, 5)

        assertEquals("5", window.draft(player)?.settings?.get("amount"))
    }

    @Test
    fun `a tile field opens the world map on the player`() {
        val player = configuring(0, step("walk"))

        ui.click(player, BuilderWidgets.rowFace(0))

        assertEquals(mapOf("x" to 3200, "y" to 3200), fields(player, "MapPickMessageWriter").single())
    }

    @Test
    fun `a tile picked on the map sets the step being configured`() {
        val player = configuring(0, step("walk"))

        ui.picked(player, Tile(3086, 3233))

        assertEquals("3086 3233", window.draft(player)?.settings?.get("tile"))
    }

    @Test
    fun `the Idle tab's button opens the builder`() {
        val player = login()

        ui.tab(player, FlowWidgets.TAB_OPEN_BUILDER)

        assertTrue(window.isOpen(player))
    }

    @Test
    fun `the Idle tab's run answers in the chat box`() {
        val player = login()

        ui.tab(player, FlowWidgets.TAB_RUN)

        assertEquals(listOf("Autopilot: the flow is empty. Add a step first."), TestWorld.chatbox(player))
    }
}
