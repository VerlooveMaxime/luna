package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.IdleSteps
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.flow.FakeStepType
import game.idle.flow.FlowGrammar
import game.idle.flow.FlowResolver
import game.idle.flow.StepField
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowBuilderTest {

    private val autopilot = Autopilot<FakeAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val banks = BankCatalog(listOf(Bank("draynor", "Draynor bank", Tile(3091, 3242))))
    private val builder = FlowBuilder(autopilot, FlowResolver(IdleSteps(banks).grammar))
    private val player = FakeAutopilotPlayer("maxime")

    private val chopBankDrop = listOf("chop normal", "bank nearest", "drop")
    private val dropFirst = "Step 1: drop comes after a chop step, so the flow knows what to drop"

    private fun click(widgetId: Int, times: Int = 1) = repeat(times) { builder.click(player, widgetId) }

    @Test
    fun `the first draft chops the easiest tree`() {
        assertEquals("chop normal", builder.draft(player).line())
    }

    @Test
    fun `a grammar with more fields per step than the screen shows is refused`() {
        val wide = FakeStepType("wide", List(4) { StepField.Choice("field $it") { emptyList() } })

        assertThrows<IllegalArgumentException> { FlowBuilder(autopilot, FlowResolver(FlowGrammar(listOf(wide)))) }
    }

    @Test
    fun `an unknown widget is ignored`() {
        assertEquals(ClickResult.Ignored, builder.click(player, FlowWidgets.STATUS))
    }

    @Test
    fun `opening the builder clears the last message`() {
        click(FlowWidgets.RUN)

        assertEquals(ClickResult.Open, builder.click(player, FlowWidgets.TAB_OPEN_BUILDER))
        assertEquals("", builder.message(player))
    }

    @Test
    fun `a close click is handed back`() {
        assertEquals(ClickResult.Close, builder.click(player, FlowWidgets.BUILDER_CLOSE))
    }

    @Test
    fun `a choice field cycles on click`() {
        click(FlowWidgets.DRAFT_FIELDS[0])
        click(FlowWidgets.DRAFT_FIELDS[1])
        click(FlowWidgets.DRAFT_FIELDS[2])

        assertEquals("chop 1 oak within 15", builder.draft(player).line())
    }

    @Test
    fun `a walk draft goes to the tile the player stands on`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)

        assertEquals(WalkStepType, builder.draft(player).type)
        assertEquals("walk 3200 3200", builder.draft(player).line())
    }

    @Test
    fun `an untouched walk draft follows the player`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)

        player.tile = Tile(3100, 3100)

        assertEquals("walk 3100 3100", builder.draft(player).line())
    }

    @Test
    fun `clicking the tile opens the map on the tile it holds`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)

        assertEquals(ClickResult.PickTile(Tile(3200, 3200)), builder.click(player, FlowWidgets.DRAFT_FIELDS[0]))
    }

    @Test
    fun `the map opens on the ground floor of an upstairs tile`() {
        player.idleState = IdleState(flow = listOf("walk 3086 3233 1"))
        click(FlowWidgets.rowText(0))

        assertEquals(ClickResult.PickTile(Tile(3086, 3233)), builder.click(player, FlowWidgets.DRAFT_FIELDS[0]))
    }

    @Test
    fun `a tile picked on the map goes into the walk step and stays there`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)
        click(FlowWidgets.DRAFT_FIELDS[0])

        assertEquals(ClickResult.Refresh, builder.picked(player, Tile(3086, 3233)))
        player.tile = Tile(3100, 3100)

        assertEquals("walk 3086 3233", builder.draft(player).line())
    }

    @Test
    fun `a pick nobody asked for is ignored`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)

        assertEquals(ClickResult.Ignored, builder.picked(player, Tile(3086, 3233)))
        assertEquals("walk 3200 3200", builder.draft(player).line())
    }

    @Test
    fun `a pick is taken once`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)
        click(FlowWidgets.DRAFT_FIELDS[0])
        builder.picked(player, Tile(3086, 3233))

        assertEquals(ClickResult.Ignored, builder.picked(player, Tile(3000, 3000)))
    }

    @Test
    fun `a pick for a field the draft no longer has is ignored`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)
        click(FlowWidgets.DRAFT_FIELDS[0])
        click(FlowWidgets.DRAFT_KIND)

        assertEquals(ClickResult.Ignored, builder.picked(player, Tile(3086, 3233)))
    }

    @Test
    fun `the kind cycles and each kind keeps its fields`() {
        click(FlowWidgets.DRAFT_FIELDS[0])

        click(FlowWidgets.DRAFT_KIND, times = 5)

        assertEquals(DropStepType, builder.draft(player).type)

        click(FlowWidgets.DRAFT_KIND, times = 2)

        assertEquals("chop oak", builder.draft(player).line())
    }

    @Test
    fun `a field the kind of step does not have does nothing`() {
        click(FlowWidgets.DRAFT_KIND, times = 5)

        assertEquals(ClickResult.Refresh, builder.click(player, FlowWidgets.DRAFT_FIELDS[0]))

        assertEquals("drop", builder.draft(player).line())
    }

    @Test
    fun `adding appends the draft's line and reports it`() {
        click(FlowWidgets.DRAFT_ADD)

        assertEquals(listOf("chop normal"), player.idleState.flow)
        assertEquals("Step 1 added: chop normal", builder.message(player))
    }

    @Test
    fun `adding a step the resolver refuses changes nothing`() {
        click(FlowWidgets.DRAFT_KIND, times = 5)

        click(FlowWidgets.DRAFT_ADD)

        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals(dropFirst, builder.message(player))
    }

    @Test
    fun `a full flow takes no more steps`() {
        player.idleState = IdleState(flow = List(FlowWidgets.ROWS) { "chop normal" })

        click(FlowWidgets.DRAFT_ADD)

        assertEquals(FlowWidgets.ROWS, player.idleState.flow.size)
        assertEquals("The flow is full (8 steps).", builder.message(player))
    }

    @Test
    fun `editing while running is refused`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.RUN)

        click(FlowWidgets.rowDelete(0))
        click(FlowWidgets.DRAFT_ADD)

        assertEquals(chopBankDrop, player.idleState.flow)
        assertEquals("Stop the flow before editing it.", builder.message(player))
    }

    @Test
    fun `a step moves up and down`() {
        player.idleState = IdleState(flow = chopBankDrop)

        click(FlowWidgets.rowDown(1))

        assertEquals(listOf("chop normal", "drop", "bank nearest"), player.idleState.flow)

        click(FlowWidgets.rowUp(2))

        assertEquals(chopBankDrop, player.idleState.flow)
        assertEquals("", builder.message(player))
    }

    @Test
    fun `moving past either end changes nothing`() {
        player.idleState = IdleState(flow = chopBankDrop, stepIndex = 2)

        click(FlowWidgets.rowUp(0))
        click(FlowWidgets.rowDown(2))
        click(FlowWidgets.rowDown(5))

        assertEquals(IdleState(flow = chopBankDrop, stepIndex = 2), player.idleState)
    }

    @Test
    fun `deleting a step warns when the rest no longer resolves`() {
        player.idleState = IdleState(flow = listOf("chop normal", "drop"))

        click(FlowWidgets.rowDelete(0))

        assertEquals(listOf("drop"), player.idleState.flow)
        assertEquals(dropFirst, builder.message(player))
    }

    @Test
    fun `clicking a row loads it into the draft for editing`() {
        player.idleState = IdleState(flow = listOf("walk 3086 3233", "chop willow within 5"))

        click(FlowWidgets.rowText(1))

        assertEquals(1, builder.editing(player))
        assertEquals("chop willow within 5", builder.draft(player).line())
        assertEquals("Editing step 2. Change the fields, then save.", builder.message(player))
    }

    @Test
    fun `a loaded walk keeps its own tile`() {
        player.idleState = IdleState(flow = listOf("walk 3086 3233"))

        click(FlowWidgets.rowText(0))

        assertEquals("walk 3086 3233", builder.draft(player).line())
    }

    @Test
    fun `clicking an empty row changes nothing`() {
        click(FlowWidgets.rowText(3))

        assertNull(builder.editing(player))
        assertEquals("", builder.message(player))
    }

    @Test
    fun `clicking a row the parser refuses shows why`() {
        player.idleState = IdleState(flow = listOf("chop willow @draynor"))

        click(FlowWidgets.rowText(0))

        assertNull(builder.editing(player))
        assertTrue(builder.message(player).startsWith("chop no longer takes a location"))
    }

    @Test
    fun `saving replaces the edited row`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(2))
        click(FlowWidgets.DRAFT_KIND)

        click(FlowWidgets.DRAFT_ADD)

        assertEquals(listOf("chop normal", "bank nearest", "bank nearest"), player.idleState.flow)
        assertEquals("Step 3 saved: bank nearest", builder.message(player))
        assertNull(builder.editing(player))
    }

    @Test
    fun `saving an edited row that is gone appends instead`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(2))
        player.idleState = IdleState(flow = listOf("chop normal"))

        click(FlowWidgets.DRAFT_ADD)

        assertEquals(listOf("chop normal", "drop"), player.idleState.flow)
        assertEquals("Step 2 added: drop", builder.message(player))
    }

    @Test
    fun `new step leaves editing and keeps the draft`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(1))

        click(FlowWidgets.DRAFT_NEW)

        assertNull(builder.editing(player))
        assertEquals("bank nearest", builder.draft(player).line())
        assertEquals("", builder.message(player))
    }

    @Test
    fun `moving or deleting a row drops the edit in progress`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(1))

        click(FlowWidgets.rowDelete(0))

        assertNull(builder.editing(player))
    }

    @Test
    fun `run with an empty flow explains`() {
        click(FlowWidgets.RUN)

        assertFalse(autopilot.isRunning(player))
        assertEquals("The flow is empty. Add a step first.", builder.message(player))
    }

    @Test
    fun `run with a broken flow shows the problem`() {
        player.idleState = IdleState(flow = listOf("drop"))

        click(FlowWidgets.RUN)

        assertFalse(autopilot.isRunning(player))
        assertEquals(dropFirst, builder.message(player))
    }

    @Test
    fun `run starts from the first step, on the player's tile`() {
        player.idleState = IdleState(flow = chopBankDrop, stepIndex = 2, runTile = Tile(1, 2))

        click(FlowWidgets.TAB_RUN)

        assertTrue(autopilot.isRunning(player))
        assertEquals(0, player.idleState.stepIndex)
        assertEquals(player.tile, player.idleState.runTile)
        assertEquals("Running step 1: chop normal", builder.message(player))
    }

    @Test
    fun `stop stops`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.RUN)

        click(FlowWidgets.TAB_STOP)

        assertFalse(autopilot.isRunning(player))
        assertEquals("Stopped.", builder.message(player))
    }

    @Test
    fun `clear stops, empties the flow and drops the edit in progress`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(1))
        click(FlowWidgets.RUN)

        click(FlowWidgets.CLEAR)

        assertFalse(autopilot.isRunning(player))
        assertEquals(emptyList<String>(), player.idleState.flow)
        assertNull(builder.editing(player))
        assertEquals("Flow cleared.", builder.message(player))
    }

    @Test
    fun `texts combine the state, the draft, the edit and the message`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(1))

        val texts = builder.texts(player)

        assertEquals("1. chop normal", texts[FlowWidgets.rowText(0)])
        assertEquals("nearest", texts[FlowWidgets.DRAFT_FIELDS[0]])
        assertEquals("Save step 2", texts[FlowWidgets.DRAFT_ADD])
        assertEquals("Editing step 2. Change the fields, then save.", texts[FlowWidgets.MESSAGE])
    }

    @Test
    fun `forgetting a player drops the draft, the message and the edit`() {
        player.idleState = IdleState(flow = chopBankDrop)
        click(FlowWidgets.rowText(1))
        click(FlowWidgets.DRAFT_FIELDS[0])

        builder.forget(player)

        assertEquals("chop normal", builder.draft(player).line())
        assertEquals("", builder.message(player))
        assertNull(builder.editing(player))
    }

    @Test
    fun `forgetting a player drops a pick in progress`() {
        click(FlowWidgets.DRAFT_KIND, times = 4)
        click(FlowWidgets.DRAFT_FIELDS[0])

        builder.forget(player)

        assertEquals(ClickResult.Ignored, builder.picked(player, Tile(3086, 3233)))
    }
}
