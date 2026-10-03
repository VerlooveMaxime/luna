package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.IdleSteps
import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.flow.FakeStepType
import game.idle.flow.FlowGrammar
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import game.idle.location.trees
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowBuilderTest {

    private val autopilot = Autopilot<FakeAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val steps = IdleSteps(
        LocationCatalog.parse(
            catalogJson(
                locationJson("id" to "draynor", "name" to "Draynor", "trees" to trees("willow", "normal"), "bank" to null),
                locationJson("id" to "varrock_west", "bank" to mapOf("x" to 3186, "y" to 3440)),
            ),
        ),
    )
    private val resolver = FlowResolver(steps.grammar)
    private val builder = FlowBuilder(autopilot, resolver)
    private val player = FakeAutopilotPlayer("maxime")

    private val chopBankDrop = listOf("chop normal @varrock_west", "bank deposit all", "drop")
    private val bankFirst = "Step 1: bank comes after a chop step, so the flow knows which bank to use"

    @Test
    fun `the first draft chops the easiest tree of the first location`() {
        assertEquals("chop normal @draynor", builder.draft(player).line())
    }

    @Test
    fun `a grammar with more fields per step than the screen shows is refused`() {
        val wide = FakeStepType("wide", List(4) { StepField("field $it") { emptyList() } })

        assertThrows<IllegalArgumentException> { FlowBuilder(autopilot, FlowResolver(FlowGrammar(listOf(wide)))) }
    }

    @Test
    fun `an unknown widget is ignored`() {
        assertEquals(ClickResult.Ignored, builder.click(player, FlowWidgets.STATUS))
    }

    @Test
    fun `opening the builder clears the last message`() {
        builder.click(player, FlowWidgets.RUN)

        assertEquals(ClickResult.Open, builder.click(player, FlowWidgets.TAB_OPEN_BUILDER))
        assertEquals("", builder.message(player))
    }

    @Test
    fun `a close click is handed back`() {
        assertEquals(ClickResult.Close, builder.click(player, FlowWidgets.BUILDER_CLOSE))
    }

    @Test
    fun `a field cycles on click`() {
        builder.click(player, FlowWidgets.DRAFT_FIELDS[0])

        assertEquals("chop willow @draynor", builder.draft(player).line())
    }

    @Test
    fun `changing the location moves a tree that does not grow there to one that does`() {
        builder.click(player, FlowWidgets.DRAFT_FIELDS[0])

        assertEquals(ClickResult.Refresh, builder.click(player, FlowWidgets.DRAFT_FIELDS[1]))

        assertEquals("chop normal @varrock_west", builder.draft(player).line())
    }

    @Test
    fun `the kind cycles and the chop fields are kept`() {
        builder.click(player, FlowWidgets.DRAFT_FIELDS[0])

        builder.click(player, FlowWidgets.DRAFT_KIND)

        assertEquals(DropStepType, builder.draft(player).type)

        builder.click(player, FlowWidgets.DRAFT_KIND)
        builder.click(player, FlowWidgets.DRAFT_KIND)

        assertEquals("chop willow @draynor", builder.draft(player).line())
    }

    @Test
    fun `a field the kind of step does not have does nothing`() {
        builder.click(player, FlowWidgets.DRAFT_KIND)

        assertEquals(ClickResult.Refresh, builder.click(player, FlowWidgets.DRAFT_FIELDS[0]))

        assertEquals("drop", builder.draft(player).line())
    }

    @Test
    fun `adding appends the draft's line and reports it`() {
        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(listOf("chop normal @draynor"), player.idleState.flow)
        assertEquals("Step 1 added: chop normal @draynor", builder.message(player))
    }

    @Test
    fun `adding a step the resolver refuses changes nothing`() {
        builder.click(player, FlowWidgets.DRAFT_KIND)

        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals("Step 1: drop comes after a chop step, so the flow knows what to drop", builder.message(player))
    }

    @Test
    fun `a full flow takes no more steps`() {
        player.idleState = IdleState(flow = List(FlowWidgets.ROWS) { "chop normal @draynor" })

        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(FlowWidgets.ROWS, player.idleState.flow.size)
        assertEquals("The flow is full (8 steps).", builder.message(player))
    }

    @Test
    fun `editing while running is refused`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.RUN)

        builder.click(player, FlowWidgets.rowDelete(0))
        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(chopBankDrop, player.idleState.flow)
        assertEquals("Stop the flow before editing it.", builder.message(player))
    }

    @Test
    fun `a step moves up and down`() {
        player.idleState = IdleState(flow = chopBankDrop)

        builder.click(player, FlowWidgets.rowDown(1))

        assertEquals(listOf(chopBankDrop[0], "drop", "bank deposit all"), player.idleState.flow)

        builder.click(player, FlowWidgets.rowUp(2))

        assertEquals(chopBankDrop, player.idleState.flow)
        assertEquals("", builder.message(player))
    }

    @Test
    fun `moving past either end changes nothing`() {
        player.idleState = IdleState(flow = chopBankDrop, stepIndex = 2)

        builder.click(player, FlowWidgets.rowUp(0))
        builder.click(player, FlowWidgets.rowDown(2))
        builder.click(player, FlowWidgets.rowDown(5))

        assertEquals(IdleState(flow = chopBankDrop, stepIndex = 2), player.idleState)
    }

    @Test
    fun `deleting a step warns when the rest no longer resolves`() {
        player.idleState = IdleState(flow = chopBankDrop)

        builder.click(player, FlowWidgets.rowDelete(0))

        assertEquals(listOf("bank deposit all", "drop"), player.idleState.flow)
        assertEquals(bankFirst, builder.message(player))
    }

    @Test
    fun `clicking a row loads it into the draft for editing`() {
        player.idleState = IdleState(flow = listOf("chop normal @varrock_west", "drop"))

        builder.click(player, FlowWidgets.rowText(1))

        assertEquals(1, builder.editing(player))
        assertEquals("drop", builder.draft(player).line())
        assertEquals("Editing step 2. Change the fields, then save.", builder.message(player))
    }

    @Test
    fun `clicking an empty row changes nothing`() {
        builder.click(player, FlowWidgets.rowText(3))

        assertNull(builder.editing(player))
        assertEquals("", builder.message(player))
    }

    @Test
    fun `clicking a row the parser refuses shows why`() {
        player.idleState = IdleState(flow = listOf("chop normal @varrock_west until inventory full"))

        builder.click(player, FlowWidgets.rowText(0))

        assertNull(builder.editing(player))
        assertTrue(builder.message(player).startsWith("Unexpected 'until inventory full' after the location."))
    }

    @Test
    fun `saving replaces the edited row`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(2))
        builder.click(player, FlowWidgets.DRAFT_KIND)

        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(listOf(chopBankDrop[0], "bank deposit all", "bank deposit all"), player.idleState.flow)
        assertEquals("Step 3 saved: bank deposit all", builder.message(player))
        assertNull(builder.editing(player))
    }

    @Test
    fun `saving an edited row that is gone appends instead`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(2))
        player.idleState = IdleState(flow = listOf(chopBankDrop[0]))

        builder.click(player, FlowWidgets.DRAFT_ADD)

        assertEquals(listOf(chopBankDrop[0], "drop"), player.idleState.flow)
        assertEquals("Step 2 added: drop", builder.message(player))
    }

    @Test
    fun `new step leaves editing and keeps the draft`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(1))

        builder.click(player, FlowWidgets.DRAFT_NEW)

        assertNull(builder.editing(player))
        assertEquals(BankStepType, builder.draft(player).type)
        assertEquals("", builder.message(player))
    }

    @Test
    fun `moving or deleting a row drops the edit in progress`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(1))

        builder.click(player, FlowWidgets.rowDelete(0))

        assertNull(builder.editing(player))
    }

    @Test
    fun `run with an empty flow explains`() {
        builder.click(player, FlowWidgets.RUN)

        assertFalse(autopilot.isRunning(player))
        assertEquals("The flow is empty. Add a step first.", builder.message(player))
    }

    @Test
    fun `run with a broken flow shows the problem`() {
        player.idleState = IdleState(flow = listOf("bank deposit all"))

        builder.click(player, FlowWidgets.RUN)

        assertFalse(autopilot.isRunning(player))
        assertEquals(bankFirst, builder.message(player))
    }

    @Test
    fun `run starts from the first step`() {
        player.idleState = IdleState(flow = chopBankDrop, stepIndex = 2)

        builder.click(player, FlowWidgets.TAB_RUN)

        assertTrue(autopilot.isRunning(player))
        assertEquals(0, player.idleState.stepIndex)
        assertEquals("Running step 1: ${chopBankDrop[0]}", builder.message(player))
    }

    @Test
    fun `stop stops`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.RUN)

        builder.click(player, FlowWidgets.TAB_STOP)

        assertFalse(autopilot.isRunning(player))
        assertEquals("Stopped.", builder.message(player))
    }

    @Test
    fun `clear stops, empties the flow and drops the edit in progress`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(1))
        builder.click(player, FlowWidgets.RUN)

        builder.click(player, FlowWidgets.CLEAR)

        assertFalse(autopilot.isRunning(player))
        assertEquals(emptyList<String>(), player.idleState.flow)
        assertNull(builder.editing(player))
        assertEquals("Flow cleared.", builder.message(player))
    }

    @Test
    fun `texts combine the state, the draft, the edit and the message`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(0))

        val texts = builder.texts(player)

        assertEquals("1. ${chopBankDrop[0]}", texts[FlowWidgets.rowText(0)])
        assertEquals("varrock_west", texts[FlowWidgets.DRAFT_FIELDS[1]])
        assertEquals("Save step 1", texts[FlowWidgets.DRAFT_ADD])
        assertEquals("Editing step 1. Change the fields, then save.", texts[FlowWidgets.MESSAGE])
    }

    @Test
    fun `forgetting a player drops the draft, the message and the edit`() {
        player.idleState = IdleState(flow = chopBankDrop)
        builder.click(player, FlowWidgets.rowText(1))
        builder.click(player, FlowWidgets.DRAFT_FIELDS[0])

        builder.forget(player)

        assertEquals("chop normal @draynor", builder.draft(player).line())
        assertEquals("", builder.message(player))
        assertNull(builder.editing(player))
    }
}
