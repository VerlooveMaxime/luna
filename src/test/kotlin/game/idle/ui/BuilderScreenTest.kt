package game.idle.ui

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowContext
import game.idle.flow.FlowResolver
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionContext
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderScreenTest {

    private val autopilot = Autopilot<FakeAutopilotPlayer>(FakeTickScheduler()) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val screen = BuilderScreen(autopilot, FlowResolver(CONFIGURED_TYPES), FakeNames(), slots = 3)

    private val chop = step("chop", "oak")
    private val drop = step("drop")
    private val walk = step("walk")

    private fun player(vararg steps: StepSettings) = FakeAutopilotPlayer("maxime", IdleState(steps = steps.toList()))

    private fun running(): FakeAutopilotPlayer = player(chop, drop).also { autopilot.start(it) }

    private fun say(message: String) = BuilderAnswer.Show(message = "Autopilot: $message")

    private fun click(player: FakeAutopilotPlayer, widgetId: Int, draft: StepDraft? = null) = screen.click(player, widgetId, draft)

    /** Step 1, the chop, on its configure screen. */
    private val choppingDraft = StepDraft(0, chop, new = false)

    @Test
    fun `close closes the window`() {
        assertEquals(BuilderAnswer.Close, click(player(), BuilderWidgets.CLOSE))
    }

    @Test
    fun `a widget that does nothing is ignored`() {
        assertEquals(BuilderAnswer.Ignored, click(player(), BuilderWidgets.STATUS))
    }

    @Test
    fun `run starts the flow from its first step`() {
        val player = player(chop, drop).apply { idleState = idleState.atStep(1) }

        click(player, BuilderWidgets.RUN)

        assertEquals(listOf<Any>(true, 0), listOf(autopilot.isRunning(player), player.idleState.stepIndex))
    }

    @Test
    fun `run says it started`() {
        assertEquals(say("running from step 1."), click(player(chop), BuilderWidgets.RUN))
    }

    @Test
    fun `run refuses an empty flow`() {
        assertEquals(say("the flow is empty. Add a step first."), click(player(), BuilderWidgets.RUN))
    }

    @Test
    fun `run refuses a step that cannot work, pointing at its slot`() {
        val player = player(chop, step("drop", "bad"))

        assertEquals(say("step 2 cannot work yet. See its slot warning in the builder."), click(player, BuilderWidgets.RUN))
        assertFalse(autopilot.isRunning(player))
    }

    @Test
    fun `stop stops the flow`() {
        val player = running()

        assertEquals(say("stopped."), click(player, BuilderWidgets.STOP))
        assertFalse(autopilot.isRunning(player))
    }

    @Test
    fun `clear empties the flow`() {
        val player = player(chop, drop)

        assertEquals(say("flow cleared."), click(player, BuilderWidgets.CLEAR))
        assertEquals(listOf<Any>(), player.idleState.steps)
    }

    @Test
    fun `clearing an empty flow says nothing`() {
        assertEquals(BuilderAnswer.Show(), click(player(), BuilderWidgets.CLEAR))
    }

    @Test
    fun `clear waits for the flow to stop`() {
        val player = running()

        assertEquals(BuilderAnswer.Show(message = BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.CLEAR))
        assertEquals(2, player.idleState.steps.size)
    }

    @Test
    fun `boosted levels are counted once lit`() {
        val player = player()

        click(player, BuilderWidgets.BOOSTED_LEVELS)

        assertTrue(player.idleState.countBoostedLevels)
    }

    @Test
    fun `base levels are counted again once lit`() {
        val player = FakeAutopilotPlayer("maxime", IdleState(countBoostedLevels = true))

        click(player, BuilderWidgets.BASE_LEVELS)

        assertFalse(player.idleState.countBoostedLevels)
    }

    @Test
    fun `the levels change while the flow runs`() {
        assertEquals(BuilderAnswer.Show(), click(running(), BuilderWidgets.BOOSTED_LEVELS))
    }

    @Test
    fun `a step's slot opens its configure screen`() {
        assertEquals(BuilderAnswer.Configure(StepDraft(1, drop, new = false)), click(player(chop, drop), BuilderWidgets.slotFace(1)))
    }

    @Test
    fun `a step's slot opens to look at while the flow runs`() {
        assertEquals(BuilderAnswer.Configure(choppingDraft), click(running(), BuilderWidgets.slotFace(0)))
    }

    @Test
    fun `the first free slot opens the kind picker`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.KINDS), click(player(chop), BuilderWidgets.slotFace(1)))
    }

    @Test
    fun `a free slot after it does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.slotFace(2)))
    }

    @Test
    fun `a full flow has no slot to add from`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, drop, chop), BuilderWidgets.slotFace(3)))
    }

    @Test
    fun `no step is added while the flow runs`() {
        assertEquals(BuilderAnswer.Show(message = BuilderScreen.STOP_FIRST), click(running(), BuilderWidgets.slotFace(2)))
    }

    @Test
    fun `a kind picked opens a new step's configure screen, numbered after the last`() {
        assertEquals(BuilderAnswer.Configure(StepDraft(1, StepSettings("drop"), new = true)), click(player(chop), BuilderWidgets.kindFace(1)))
    }

    @Test
    fun `a button past the kinds does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(), BuilderWidgets.kindFace(3)))
    }

    @Test
    fun `no kind is picked while the flow runs`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, BuilderScreen.STOP_FIRST), click(running(), BuilderWidgets.kindFace(0)))
    }

    @Test
    fun `back on the kind picker returns to the overview`() {
        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW), click(player(), BuilderWidgets.KINDS_BACK))
    }

    @Test
    fun `a configure click with no step being configured is ignored`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.SAVE))
    }

    @Test
    fun `a widget the configure screen does not know is ignored`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.HEADER_NAME, choppingDraft))
    }

    @Test
    fun `back discards the step's changes`() {
        val player = player(chop)

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW), click(player, BuilderWidgets.BACK, choppingDraft.with("word", "willow")))
        assertEquals(listOf(chop), player.idleState.steps)
    }

    @Test
    fun `save replaces the step configured and returns to the overview`() {
        val player = player(chop, drop)

        val answer = click(player, BuilderWidgets.SAVE, choppingDraft.with("word", "willow"))

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, "Autopilot: step 1 saved."), answer)
        assertEquals(listOf(step("chop", "willow"), drop), player.idleState.steps)
    }

    @Test
    fun `save puts a new step after the last`() {
        val player = player(chop)

        click(player, BuilderWidgets.SAVE, StepDraft(1, drop, new = true))

        assertEquals(listOf(chop, drop), player.idleState.steps)
    }

    @Test
    fun `save keeps a step that cannot work`() {
        val player = player(chop)

        click(player, BuilderWidgets.SAVE, StepDraft(1, step("drop", "bad"), new = true))

        assertEquals(listOf(chop, step("drop", "bad")), player.idleState.steps)
    }

    @Test
    fun `a step removed since its screen opened is saved after the last`() {
        val player = player(chop)

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, "Autopilot: step 2 saved."), click(player, BuilderWidgets.SAVE, StepDraft(4, drop, new = false)))
        assertEquals(listOf(chop, drop), player.idleState.steps)
    }

    @Test
    fun `a new step finds no room in a full flow`() {
        val player = player(chop, drop, chop)
        val draft = StepDraft(3, drop, new = true, typing = "amount")

        assertEquals(BuilderAnswer.Configure(draft.notTyping(), "Autopilot: the flow has room for 3 steps."), click(player, BuilderWidgets.SAVE, draft))
        assertEquals(3, player.idleState.steps.size)
    }

    @Test
    fun `save waits for the flow to stop`() {
        val player = running()
        val draft = choppingDraft.with("word", "willow")

        assertEquals(BuilderAnswer.Configure(draft, BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.SAVE, draft))
        assertEquals(listOf(chop, drop), player.idleState.steps)
    }

    @Test
    fun `delete removes the step and returns to the overview`() {
        val player = player(chop, drop)

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, "Autopilot: step 1 deleted."), click(player, BuilderWidgets.DELETE, choppingDraft))
        assertEquals(listOf(drop), player.idleState.steps)
    }

    @Test
    fun `delete does nothing on a step not saved yet`() {
        val draft = StepDraft(1, drop, new = true, typing = "amount")

        assertEquals(BuilderAnswer.Configure(draft.notTyping()), click(player(chop), BuilderWidgets.DELETE, draft))
    }

    @Test
    fun `delete waits for the flow to stop`() {
        val player = running()

        assertEquals(BuilderAnswer.Configure(choppingDraft, BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.DELETE, choppingDraft))
        assertEquals(2, player.idleState.steps.size)
    }

    @Test
    fun `a step removed since its screen opened is not deleted again`() {
        val player = player(chop)

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW), click(player, BuilderWidgets.DELETE, StepDraft(3, drop, new = false)))
        assertEquals(listOf(chop), player.idleState.steps)
    }

    @Test
    fun `a search field opens the search over the steps before and the bank, nothing typed any more`() {
        val player = player(step("chop", "gather"), drop)
        val draft = StepDraft(1, step("chop", "oak"), new = false, typing = "amount")

        val answer = click(player, BuilderWidgets.rowFace(0), draft)

        val before = FlowContext(gathered = setOf(1), gatheredBy = mapOf(1 to 1))
        val context = OptionContext(settings = draft.settings, before = before, input = InputSource.BANK)
        assertEquals(BuilderAnswer.Search(draft.notTyping(), TREE_SEARCH, context), answer)
    }

    @Test
    fun `a typed field opens the amount prompt, framing the field`() {
        assertEquals(BuilderAnswer.Amount(choppingDraft.copy(typing = "amount")), click(player(chop), BuilderWidgets.rowFace(1), choppingDraft))
    }

    @Test
    fun `a field on the right column opens like the left's`() {
        assertEquals(BuilderAnswer.Amount(choppingDraft.copy(typing = "within")), click(player(chop), BuilderWidgets.rowFace(5), choppingDraft))
    }

    @Test
    fun `a tile field opens the world map on the tile it holds`() {
        val draft = StepDraft(0, walk.with("tile", "3086 3233"), new = false)

        assertEquals(BuilderAnswer.PickTile(draft, Tile(3086, 3233)), click(player(walk), BuilderWidgets.rowFace(0), draft))
    }

    @Test
    fun `a tile field without a tile opens the world map on the player`() {
        val draft = StepDraft(0, walk, new = false)

        assertEquals(BuilderAnswer.PickTile(draft, Tile(3200, 3200)), click(player(walk), BuilderWidgets.rowFace(0), draft))
    }

    @Test
    fun `a tile field holding no tile opens the world map on the player`() {
        val draft = StepDraft(0, walk.with("tile", "north"), new = false)

        assertEquals(BuilderAnswer.PickTile(draft, Tile(3200, 3200)), click(player(walk), BuilderWidgets.rowFace(0), draft))
    }

    @Test
    fun `a note's row does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.rowFace(2), choppingDraft))
    }

    @Test
    fun `a row without a setting does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.rowFace(3), choppingDraft))
    }

    @Test
    fun `a step of a kind that is gone has no settings`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.rowFace(0), StepDraft(0, StepSettings("gone"), new = false)))
    }

    @Test
    fun `a field waits for the flow to stop`() {
        assertEquals(BuilderAnswer.Configure(choppingDraft, BuilderScreen.STOP_FIRST), click(running(), BuilderWidgets.rowFace(0), choppingDraft))
    }

    @Test
    fun `the amount's button removes the amount`() {
        val draft = choppingDraft.with("amount", "5")

        assertEquals(BuilderAnswer.Configure(choppingDraft), click(player(chop), BuilderWidgets.rowButtonFace(1), draft))
    }

    @Test
    fun `the amount's button waits for the flow to stop`() {
        assertEquals(BuilderAnswer.Configure(choppingDraft, BuilderScreen.STOP_FIRST), click(running(), BuilderWidgets.rowButtonFace(1), choppingDraft))
    }

    @Test
    fun `a row without a button does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop), BuilderWidgets.rowButtonFace(0), choppingDraft))
    }

    @Test
    fun `a typed number in range sets the field being typed`() {
        val answer = screen.typed(choppingDraft.copy(typing = "amount"), 25)

        assertEquals(BuilderAnswer.Configure(choppingDraft.with("amount", "25")), answer)
    }

    @Test
    fun `a typed number out of range says the field's rule`() {
        val answer = screen.typed(choppingDraft.copy(typing = "within"), 40)

        assertEquals(BuilderAnswer.Configure(choppingDraft, "Autopilot: within takes 1 to 32 tiles."), answer)
    }

    @Test
    fun `a typed number below the range says the field's rule`() {
        val answer = screen.typed(choppingDraft.copy(typing = "amount"), 0)

        assertEquals(BuilderAnswer.Configure(choppingDraft, "Autopilot: the amount takes 1 to 1000."), answer)
    }

    @Test
    fun `a typed number with no field being typed is ignored`() {
        assertEquals(listOf(BuilderAnswer.Ignored, BuilderAnswer.Ignored), listOf(screen.typed(choppingDraft, 5), screen.typed(null, 5)))
    }

    @Test
    fun `a pick sets the search's setting`() {
        val answer = screen.picked(choppingDraft.copy(typing = "amount"), opened = choppingDraft, key = "word", value = "willow")

        assertEquals(BuilderAnswer.Configure(choppingDraft.with("word", "willow")), answer)
    }

    @Test
    fun `a pick for another step, or once the screen is gone, is ignored`() {
        val other = StepDraft(1, drop, new = false)

        assertEquals(
            listOf(BuilderAnswer.Ignored, BuilderAnswer.Ignored),
            listOf(screen.picked(other, choppingDraft, "word", "willow"), screen.picked(null, choppingDraft, "word", "willow")),
        )
    }

    @Test
    fun `a tile picked on the map goes into the step's tile`() {
        val draft = StepDraft(0, walk, new = false)

        assertEquals(BuilderAnswer.Configure(draft.with("tile", "3086 3233")), screen.pickedTile(draft, Tile(3086, 3233)))
    }

    @Test
    fun `a tile picked for a step without one, or once the screen is gone, is ignored`() {
        assertEquals(
            listOf(BuilderAnswer.Ignored, BuilderAnswer.Ignored),
            listOf(screen.pickedTile(choppingDraft, Tile(3086, 3233)), screen.pickedTile(null, Tile(3086, 3233))),
        )
    }

    @Test
    fun `the Idle tab's button opens the builder`() {
        assertEquals(BuilderAnswer.Open, screen.tab(player(), FlowWidgets.TAB_OPEN_BUILDER))
    }

    @Test
    fun `the Idle tab's run and stop work as the builder's`() {
        val player = player(chop)

        assertEquals(listOf(say("running from step 1."), say("stopped.")), listOf(screen.tab(player, FlowWidgets.TAB_RUN), screen.tab(player, FlowWidgets.TAB_STOP)))
    }

    @Test
    fun `the Idle tab's other widgets do nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(player(), FlowWidgets.TAB_STATUS_1))
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
