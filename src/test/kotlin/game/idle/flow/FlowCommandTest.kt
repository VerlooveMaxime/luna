package game.idle.flow

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.autopilot.IdleSteps
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowCommandTest {

    private val scheduler = FakeTickScheduler()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val banks = BankCatalog(listOf(Bank("varrock_west", "Varrock west bank", Tile(3186, 3440))))
    private val command = FlowCommand(autopilot, FlowResolver(IdleSteps(banks).grammar))

    private val player = FakeAutopilotPlayer("maxime")

    private fun flow(vararg args: String) = command.flow(player, args.toList())

    @Test
    fun `add appends a valid step`() {
        flow("add", "chop", "normal")

        assertEquals(listOf("chop normal"), player.idleState.flow)
        assertEquals(listOf("Autopilot: step 1: chop normal"), player.told)
    }

    @Test
    fun `add rejects a step that does not fit the flow`() {
        flow("add", "drop")

        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals(listOf("Autopilot: Step 1: drop comes after a chop step, so the flow knows what to drop"), player.told)
    }

    @Test
    fun `add while running is refused`() {
        flow("add", "chop", "normal")
        flow("run")

        flow("add", "drop")

        assertEquals(listOf("chop normal"), player.idleState.flow)
        assertEquals("Autopilot: stop the flow first (::flow stop).", player.told.last())
    }

    @Test
    fun `list shows an empty flow`() {
        flow("list")

        assertEquals(listOf("Autopilot: the flow is empty. ::flow add <step>"), player.told)
    }

    @Test
    fun `list numbers the steps and shows where it is`() {
        player.idleState = IdleState(flow = listOf("chop normal", "drop"), stepIndex = 1)

        flow("list")

        assertEquals(listOf("Autopilot: flow (stopped, at step 2):", "1. chop normal", "2. drop"), player.told)
    }

    @Test
    fun `list says when it is running`() {
        flow("add", "chop", "normal")
        flow("run")

        flow("list")

        assertEquals("Autopilot: flow (running, at step 1):", player.told[2])
    }

    @Test
    fun `clear stops and empties the flow`() {
        flow("add", "chop", "normal")
        flow("run")

        flow("clear")

        assertFalse(autopilot.isRunning(player))
        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals("Autopilot: flow cleared.", player.told.last())
    }

    @Test
    fun `run starts from the first step, on the player's tile`() {
        player.idleState = IdleState(flow = listOf("chop normal", "drop"), stepIndex = 1, runTile = Tile(1, 2))

        flow("run")

        assertTrue(autopilot.isRunning(player))
        assertEquals(0, player.idleState.stepIndex)
        assertEquals(player.tile, player.idleState.runTile)
        assertEquals(listOf("Autopilot: running step 1: chop normal"), player.told)
    }

    @Test
    fun `resume continues from the saved step and its run tile`() {
        player.idleState = IdleState(flow = listOf("chop normal", "drop"), stepIndex = 1, runTile = Tile(1, 2))

        flow("resume")

        assertEquals(1, player.idleState.stepIndex)
        assertEquals(Tile(1, 2), player.idleState.runTile)
        assertEquals(listOf("Autopilot: running step 2: drop"), player.told)
    }

    @Test
    fun `run with an empty flow explains`() {
        flow("run")

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: the flow is empty. ::flow add <step>"), player.told)
    }

    @Test
    fun `run with a flow that no longer resolves explains`() {
        player.idleState = IdleState(flow = listOf("bank @atlantis"))

        flow("run")

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: Step 1: Unknown bank 'atlantis'. Banks: varrock_west"), player.told)
    }

    @Test
    fun `stop stops`() {
        flow("add", "chop", "normal")
        flow("run")

        flow("stop")

        assertFalse(autopilot.isRunning(player))
        assertEquals("Autopilot: stopped.", player.told.last())
    }

    @Test
    fun `an unknown verb prints the usage`() {
        flow("dance")

        assertEquals(
            listOf("::flow add <step> | list | clear | run | resume | stop. Steps: chop [<n>] <tree> [within <r>], fish [<n>] <fish> [within <r>], light [<n>], cook [<n>] [within <r>], " +
                "walk <x> <y>, drop, bank nearest|@<bank>"),
            player.told,
        )
    }

    @Test
    fun `no verb prints the usage`() {
        flow()

        assertEquals(1, player.told.size)
    }

    @Test
    fun `idle with a tree chops it around the player and drops the logs`() {
        player.idleState = IdleState(runTile = Tile(1, 2))

        command.idle(player, listOf("oak"))

        assertTrue(autopilot.isRunning(player))
        assertEquals(listOf("chop oak", "drop"), player.idleState.flow)
        assertEquals(player.tile, player.idleState.runTile)
        assertEquals(listOf("Autopilot: running step 1: chop oak"), player.told)
    }

    @Test
    fun `idle alone while running stops`() {
        command.idle(player, listOf("oak"))

        command.idle(player, emptyList())

        assertFalse(autopilot.isRunning(player))
        assertEquals("Autopilot: stopped.", player.told.last())
    }

    @Test
    fun `idle alone while stopped says how to use it`() {
        command.idle(player, emptyList())

        assertEquals(listOf("Autopilot: ::idle <tree> chops it around you."), player.told)
    }

    @Test
    fun `idle with a bad tree explains and changes nothing`() {
        command.idle(player, listOf("palm"))

        assertFalse(autopilot.isRunning(player))
        assertEquals(emptyList<String>(), player.idleState.flow)
        assertNull(player.idleState.runTile)
        assertEquals(listOf("Autopilot: Step 1: 'palm' is not a kind of tree"), player.told)
    }
}
