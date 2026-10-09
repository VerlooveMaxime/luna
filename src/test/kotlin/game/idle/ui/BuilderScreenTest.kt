package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.flow.FakeStepType
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowResolver
import game.idle.flow.StepTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderScreenTest {

    private val autopilot = Autopilot<FakeAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val types = StepTypes(listOf(FakeStepType("chop"), FakeStepType("drop")))
    private val screen = BuilderScreen(autopilot, FlowResolver(types), slots = 3)

    private val chop = step("chop", "oak")
    private val drop = step("drop")

    private fun player(vararg steps: game.idle.flow.StepSettings) = FakeAutopilotPlayer("maxime", IdleState(steps = steps.toList()))

    private fun running(): FakeAutopilotPlayer = player(chop, drop).also { autopilot.start(it) }

    private fun say(message: String) = BuilderAnswer.Show(message = "Autopilot: $message")

    @Test
    fun `close closes the window`() {
        assertEquals(BuilderAnswer.Close, screen.click(player(), BuilderWidgets.CLOSE))
    }

    @Test
    fun `a widget that does nothing is ignored`() {
        assertEquals(BuilderAnswer.Ignored, screen.click(player(), BuilderWidgets.STATUS))
    }

    @Test
    fun `run starts the flow from its first step`() {
        val player = player(chop, drop).apply { idleState = idleState.atStep(1) }

        screen.click(player, BuilderWidgets.RUN)

        assertEquals(listOf<Any>(true, 0), listOf(autopilot.isRunning(player), player.idleState.stepIndex))
    }

    @Test
    fun `run says it started`() {
        assertEquals(say("running from step 1."), screen.click(player(chop), BuilderWidgets.RUN))
    }

    @Test
    fun `run refuses an empty flow`() {
        assertEquals(say("the flow is empty. Add a step first."), screen.click(player(), BuilderWidgets.RUN))
    }

    @Test
    fun `run refuses a step that cannot work, pointing at its slot`() {
        val player = player(chop, step("drop", "bad"))

        assertEquals(say("step 2 cannot work yet. See its slot warning in the builder."), screen.click(player, BuilderWidgets.RUN))
        assertFalse(autopilot.isRunning(player))
    }

    @Test
    fun `stop stops the flow`() {
        val player = running()

        assertEquals(say("stopped."), screen.click(player, BuilderWidgets.STOP))
        assertFalse(autopilot.isRunning(player))
    }

    @Test
    fun `clear empties the flow`() {
        val player = player(chop, drop)

        assertEquals(say("flow cleared."), screen.click(player, BuilderWidgets.CLEAR))
        assertEquals(listOf<Any>(), player.idleState.steps)
    }

    @Test
    fun `clearing an empty flow says nothing`() {
        assertEquals(BuilderAnswer.Show(), screen.click(player(), BuilderWidgets.CLEAR))
    }

    @Test
    fun `clear waits for the flow to stop`() {
        val player = running()

        assertEquals(BuilderAnswer.Show(message = BuilderScreen.STOP_FIRST), screen.click(player, BuilderWidgets.CLEAR))
        assertEquals(2, player.idleState.steps.size)
    }

    @Test
    fun `boosted levels are counted once lit`() {
        val player = player()

        screen.click(player, BuilderWidgets.BOOSTED_LEVELS)

        assertTrue(player.idleState.countBoostedLevels)
    }

    @Test
    fun `base levels are counted again once lit`() {
        val player = FakeAutopilotPlayer("maxime", IdleState(countBoostedLevels = true))

        screen.click(player, BuilderWidgets.BASE_LEVELS)

        assertFalse(player.idleState.countBoostedLevels)
    }

    @Test
    fun `the levels change while the flow runs`() {
        val player = running()

        assertEquals(BuilderAnswer.Show(), screen.click(player, BuilderWidgets.BOOSTED_LEVELS))
    }

    @Test
    fun `a step's slot says its configure screen comes next`() {
        assertEquals(say("step 2 (drop) opens its configure screen in S06b."), screen.click(player(chop, drop), BuilderWidgets.slotFace(1)))
    }

    @Test
    fun `the first free slot opens the kind picker`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.KINDS), screen.click(player(chop), BuilderWidgets.slotFace(1)))
    }

    @Test
    fun `a free slot after it does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.click(player(chop), BuilderWidgets.slotFace(2)))
    }

    @Test
    fun `a full flow has no slot to add from`() {
        assertEquals(BuilderAnswer.Ignored, screen.click(player(chop, drop, chop), BuilderWidgets.slotFace(3)))
    }

    @Test
    fun `no step is added while the flow runs`() {
        assertEquals(BuilderAnswer.Show(message = BuilderScreen.STOP_FIRST), screen.click(running(), BuilderWidgets.slotFace(2)))
    }

    @Test
    fun `a kind picked returns to the overview and says what comes next`() {
        assertEquals(
            BuilderAnswer.Show(BuilderPage.OVERVIEW, "Autopilot: a new drop step: its configure screen comes in S06b."),
            screen.click(player(chop), BuilderWidgets.kindFace(1)),
        )
    }

    @Test
    fun `a button past the kinds does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.click(player(), BuilderWidgets.kindFace(2)))
    }

    @Test
    fun `no kind is picked while the flow runs`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, BuilderScreen.STOP_FIRST), screen.click(running(), BuilderWidgets.kindFace(0)))
    }

    @Test
    fun `back returns to the overview`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW), screen.click(player(), BuilderWidgets.KINDS_BACK))
    }

    @Test
    fun `a slot dragged onto a later one goes there, the ones between moving up`() {
        val player = player(chop, drop, step("chop", "willow"))

        screen.arrange(player, from = 0, to = 2)

        assertEquals(listOf(drop, step("chop", "willow"), chop), player.idleState.steps)
    }

    @Test
    fun `a slot dragged onto an earlier one goes there, the ones between moving down`() {
        val player = player(chop, drop, step("chop", "willow"))

        screen.arrange(player, from = 2, to = 0)

        assertEquals(listOf(step("chop", "willow"), chop, drop), player.idleState.steps)
    }

    @Test
    fun `a slot dropped on a free slot goes last`() {
        val player = player(chop, drop)

        screen.arrange(player, from = 0, to = 2)

        assertEquals(listOf(drop, chop), player.idleState.steps)
    }

    @Test
    fun `a slot dropped before the first goes first`() {
        val player = player(chop, drop)

        screen.arrange(player, from = 1, to = -1)

        assertEquals(listOf(drop, chop), player.idleState.steps)
    }

    @Test
    fun `a drag shows the flow as it now is`() {
        assertEquals(BuilderAnswer.Show(), screen.arrange(player(chop, drop), from = 0, to = 1))
    }

    @Test
    fun `dragging a free slot does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.arrange(player(chop), from = 1, to = 0))
    }

    @Test
    fun `a drag from no slot does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.arrange(player(chop), from = -1, to = 0))
    }

    @Test
    fun `nothing moves while the flow runs`() {
        val player = running()

        assertEquals(BuilderAnswer.Show(message = BuilderScreen.STOP_FIRST), screen.arrange(player, from = 0, to = 1))
        assertEquals(listOf(chop, drop), player.idleState.steps)
    }
}
