package game.idle.ui

import game.idle.IdleState
import game.idle.SavedFlow
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowContext
import game.idle.flow.FlowIds
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.SavedFlows
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
    private val screen = BuilderScreen(autopilot, FlowResolver(CONFIGURED_TYPES), ReflexResolver(emptySet()), FakeNames(), slots = 3, SavedFlows(2))

    private val chop = step("chop", "oak")
    private val drop = step("drop")
    private val walk = step("walk")

    private fun player(vararg steps: StepSettings) = FakeAutopilotPlayer("maxime", IdleState(steps = steps.toList()))

    /** [steps] as a save through the builder keeps them, each given an id. */
    private fun numbered(vararg steps: StepSettings) = FlowIds.steps(steps.toList())

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
    fun `run refuses a reflex that cannot work, pointing at its row`() {
        val player = player(chop).apply { idleState = idleState.copy(reflexes = listOf(ReflexSettings(1), ReflexSettings(2, mapOf("below" to "0")))) }

        assertEquals(say("reflex 2 cannot work yet. See its row in the builder."), click(player, BuilderWidgets.RUN))
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
    fun `clear empties the reflexes too, a cleared flow being a new one`() {
        val player = player(chop).apply { idleState = idleState.copy(reflexes = listOf(ReflexSettings(1))) }

        click(player, BuilderWidgets.CLEAR)

        assertEquals(listOf<Any>(), player.idleState.reflexes)
    }

    @Test
    fun `a flow of reflexes alone is cleared`() {
        val player = player().apply { idleState = idleState.copy(reflexes = listOf(ReflexSettings(1))) }

        assertEquals(say("flow cleared."), click(player, BuilderWidgets.CLEAR))
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
        assertEquals(BuilderAnswer.Ignored, click(player(), BuilderWidgets.kindFace(CONFIGURED_TYPES.all.size)))
    }

    @Test
    fun `a new step starts with the settings its kind gives it after the steps before`() {
        val answer = click(player(chop), BuilderWidgets.kindFace(3))

        assertEquals(BuilderAnswer.Configure(StepDraft(1, StepSettings("light", mapOf("input" to "earlier", "logs" to "1511")), new = true)), answer)
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
        assertEquals(numbered(step("chop", "willow"), drop), player.idleState.steps)
    }

    @Test
    fun `save puts a new step after the last`() {
        val player = player(chop)

        click(player, BuilderWidgets.SAVE, StepDraft(1, drop, new = true))

        assertEquals(numbered(chop, drop), player.idleState.steps)
    }

    @Test
    fun `a new step gets the id after the highest of the flow`() {
        val player = player(chop.copy(id = 4))

        click(player, BuilderWidgets.SAVE, StepDraft(1, drop, new = true))

        assertEquals(listOf(4, 5), player.idleState.steps.map { it.id })
    }

    @Test
    fun `save keeps a step that cannot work`() {
        val player = player(chop)

        click(player, BuilderWidgets.SAVE, StepDraft(1, step("drop", "bad"), new = true))

        assertEquals(numbered(chop, step("drop", "bad")), player.idleState.steps)
    }

    @Test
    fun `a step removed since its screen opened is saved after the last`() {
        val player = player(chop)

        assertEquals(BuilderAnswer.Show(BuilderPage.OVERVIEW, "Autopilot: step 2 saved."), click(player, BuilderWidgets.SAVE, StepDraft(4, drop, new = false)))
        assertEquals(numbered(chop, drop), player.idleState.steps)
    }

    @Test
    fun `a new step finds no room in a full flow`() {
        val player = player(chop, drop, chop)
        val draft = StepDraft(3, drop, new = true, typing = Typing("amount"))

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
        val draft = StepDraft(1, drop, new = true, typing = Typing("amount"))

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
        val draft = StepDraft(1, step("chop", "oak"), new = false, typing = Typing("amount"))

        val answer = click(player, BuilderWidgets.rowFace(0), draft)

        val before = FlowContext(gathered = setOf(1), gatheredBy = mapOf(1 to 1), lap = setOf(1))
        val context = OptionContext(settings = draft.settings, before = before, input = InputSource.BANK)
        assertEquals(BuilderAnswer.Search(draft.notTyping(), TREE_SEARCH, context), answer)
    }

    @Test
    fun `a typed field opens the amount prompt, framing the field`() {
        assertEquals(BuilderAnswer.Amount(choppingDraft.copy(typing = Typing("amount"))), click(player(chop), BuilderWidgets.rowFace(1), choppingDraft))
    }

    @Test
    fun `a field on the right column opens like the left's`() {
        assertEquals(BuilderAnswer.Amount(choppingDraft.copy(typing = Typing("within"))), click(player(chop), BuilderWidgets.rowFace(6), choppingDraft))
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
        val answer = screen.typed(choppingDraft.copy(typing = Typing("amount")), 25)

        assertEquals(BuilderAnswer.Configure(choppingDraft.with("amount", "25")), answer)
    }

    @Test
    fun `a typed number out of range says the field's rule`() {
        val answer = screen.typed(choppingDraft.copy(typing = Typing("within")), 40)

        assertEquals(BuilderAnswer.Configure(choppingDraft, "Autopilot: within takes 1 to 32 tiles."), answer)
    }

    @Test
    fun `a typed number below the range says the field's rule`() {
        val answer = screen.typed(choppingDraft.copy(typing = Typing("amount")), 0)

        assertEquals(BuilderAnswer.Configure(choppingDraft, "Autopilot: the amount takes 1 or more."), answer)
    }

    @Test
    fun `a typed number with no field being typed is ignored`() {
        assertEquals(listOf(BuilderAnswer.Ignored, BuilderAnswer.Ignored), listOf(screen.typed(choppingDraft, 5), screen.typed(null, 5)))
    }

    @Test
    fun `a pick sets the search's setting`() {
        val answer = screen.picked(choppingDraft.copy(typing = Typing("amount")), opened = choppingDraft, key = "word", value = "willow")

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
    fun `the Idle tab's run and stop work as the builder's`() {
        val player = player(chop)

        assertEquals(listOf(say("running from step 1."), say("stopped.")), listOf(screen.tab(player, FlowWidgets.TAB_RUN), screen.tab(player, FlowWidgets.TAB_STOP)))
    }

    @Test
    fun `the Idle tab's other widgets do nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(player(), FlowWidgets.TAB_STATUS_1))
    }

    private val cows = SavedFlow(1, "Cows", listOf(drop, chop))

    /** A player whose flow is [steps], belonging to [slot], with the saved flow Cows in slot 2. */
    private fun keeping(vararg steps: StepSettings, slot: Int? = null) =
        FakeAutopilotPlayer("maxime", IdleState(steps = steps.toList(), savedFlows = listOf(cows), savedSlot = slot))

    @Test
    fun `Load on another slot makes its flow the current one`() {
        val player = keeping(chop)

        screen.tab(player, FlowWidgets.rowLoad(1))

        assertEquals(listOf(drop, chop), player.idleState.steps)
        assertEquals(1, player.idleState.savedSlot)
    }

    @Test
    fun `Load on another slot opens the builder on it and says which flow it loaded`() {
        assertEquals(BuilderAnswer.Replaced("Autopilot: loaded 'Cows'."), screen.tab(keeping(chop), FlowWidgets.rowLoad(1)))
    }

    @Test
    fun `Load on another slot stops a running flow first and says so`() {
        val player = keeping(chop).also { autopilot.start(it) }

        screen.tab(player, FlowWidgets.rowLoad(1))

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: stopped."), player.told)
    }

    @Test
    fun `Load on the current flow's slot opens the builder on it as it is`() {
        val player = keeping(chop, slot = 1)

        val answer = screen.tab(player, FlowWidgets.rowLoad(1))

        assertEquals(BuilderAnswer.Open, answer)
        assertEquals(listOf(chop), player.idleState.steps)
    }

    @Test
    fun `Load on the running flow's slot leaves it running`() {
        val player = keeping(chop, slot = 1).also { autopilot.start(it) }

        screen.tab(player, FlowWidgets.rowLoad(1))

        assertTrue(autopilot.isRunning(player))
    }

    @Test
    fun `Load on an empty slot does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(keeping(chop), FlowWidgets.rowLoad(0)))
    }

    @Test
    fun `New on an empty slot starts an empty flow belonging to it`() {
        val player = keeping(chop, slot = 1)

        screen.tab(player, FlowWidgets.rowNew(0))

        assertEquals(IdleState(savedFlows = listOf(cows), savedSlot = 0), player.idleState)
    }

    @Test
    fun `New opens the builder on the new flow`() {
        assertEquals(BuilderAnswer.Replaced(), screen.tab(keeping(chop), FlowWidgets.rowNew(0)))
    }

    @Test
    fun `New stops a running flow first`() {
        val player = keeping(chop).also { autopilot.start(it) }

        screen.tab(player, FlowWidgets.rowNew(0))

        assertFalse(autopilot.isRunning(player))
    }

    @Test
    fun `New on the new flow's own slot opens it as it is`() {
        val player = keeping(chop, slot = 0)

        assertEquals(BuilderAnswer.Open, screen.tab(player, FlowWidgets.rowNew(0)))
        assertEquals(listOf(chop), player.idleState.steps)
    }

    @Test
    fun `New on a slot holding a flow does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(keeping(chop), FlowWidgets.rowNew(1)))
    }

    @Test
    fun `x empties another slot and says which flow went`() {
        val player = keeping(chop)

        val answer = screen.tab(player, FlowWidgets.rowDelete(1))

        assertEquals(say("'Cows' deleted."), answer)
        assertEquals(listOf<Any>(emptyList<SavedFlow>(), listOf(chop)), listOf(player.idleState.savedFlows, player.idleState.steps))
    }

    @Test
    fun `x on another slot leaves a running flow running`() {
        val player = keeping(chop).also { autopilot.start(it) }

        screen.tab(player, FlowWidgets.rowDelete(1))

        assertTrue(autopilot.isRunning(player))
    }

    @Test
    fun `x on another slot keeps a flow belonging to its own slot`() {
        val player = keeping(chop, slot = 0)

        screen.tab(player, FlowWidgets.rowDelete(1))

        assertEquals(listOf<Any?>(listOf(chop), 0), listOf(player.idleState.steps, player.idleState.savedSlot))
    }

    @Test
    fun `x on the current flow's slot empties the flow too`() {
        val player = keeping(drop, chop, slot = 1)

        screen.tab(player, FlowWidgets.rowDelete(1))

        assertEquals(IdleState(), player.idleState)
    }

    @Test
    fun `x on the running flow's slot stops it first and says so`() {
        val player = keeping(drop, chop, slot = 1).also { autopilot.start(it) }

        screen.tab(player, FlowWidgets.rowDelete(1))

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: stopped."), player.told)
    }

    @Test
    fun `x on an empty slot does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(keeping(chop), FlowWidgets.rowDelete(0)))
    }

    @Test
    fun `a row past the player's saved-flow slots does nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.tab(keeping(chop), FlowWidgets.rowLoad(2)))
    }

    @Test
    fun `the builder's Save saves over the flow's own saved flow, from its name`() {
        assertEquals(BuilderAnswer.Name(1, "Save over 'Cows' as:", "Cows"), click(keeping(chop, slot = 1), BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `the builder's Save names a new flow for its own slot`() {
        val player = keeping(chop).also { screen.tab(it, FlowWidgets.rowNew(0)) }.apply { idleState = idleState.withFlow(listOf(chop)) }

        assertEquals(BuilderAnswer.Name(0, "Name for this flow:", ""), click(player, BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `the builder's Save puts a flow of no slot in the first empty one`() {
        assertEquals(BuilderAnswer.Name(0, "Name for this flow:", ""), click(keeping(chop), BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `the builder's Save of a flow of no slot, every slot taken, points to the tab`() {
        val player = keeping(chop).apply { idleState = idleState.copy(savedFlows = idleState.savedFlows + SavedFlow(0, "Oaks", listOf(chop))) }

        assertEquals(say("every saved-flow slot is taken. Save over one in the Idle tab."), click(player, BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `the builder's Save works while the flow runs`() {
        val player = keeping(chop, slot = 1).also { autopilot.start(it) }

        assertEquals(BuilderAnswer.Name(1, "Save over 'Cows' as:", "Cows"), click(player, BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `the builder's Save with an empty flow says so`() {
        assertEquals(say("the flow is empty. Add a step first."), click(keeping(slot = 0), BuilderWidgets.SAVE_FLOW))
    }

    @Test
    fun `a name saves the flow in its slot, which it then belongs to`() {
        val player = keeping(chop)

        screen.named(player, 0, "Willows")

        assertEquals(listOf(SavedFlow(0, "Willows", listOf(chop)), cows), player.idleState.savedFlows)
        assertEquals(0, player.idleState.savedSlot)
    }

    @Test
    fun `a saved flow is named in the chat box without the spaces around it`() {
        assertEquals(say("flow saved as 'Willows'."), screen.named(keeping(chop), 0, " Willows "))
    }

    @Test
    fun `a name for a flow emptied since Save saves nothing`() {
        assertEquals(say("the flow is empty. Add a step first."), screen.named(keeping(), 0, "Willows"))
    }

    @Test
    fun `a name the rules refuse says the rule`() {
        assertEquals(say("a flow's name is 1 to 20 characters."), screen.named(keeping(chop), 0, "x".repeat(21)))
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

    private val light = StepSettings("light", mapOf("input" to "earlier", "logs" to "1511"))
    private val lighting = StepDraft(1, light, new = false)

    @Test
    fun `a toggle's button keeps its choice`() {
        assertEquals(BuilderAnswer.Configure(lighting.with("input", "bank")), click(player(chop, light), BuilderWidgets.toggleFace(0, 2, 1), lighting))
    }

    @Test
    fun `a toggle button on a row without a toggle does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, light), BuilderWidgets.toggleFace(1, 2, 0), lighting))
    }

    @Test
    fun `a toggle button past the toggle's choices does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, light), BuilderWidgets.toggleFace(0, 3, 2), lighting))
    }

    @Test
    fun `no toggle changes while the flow runs`() {
        val player = player(chop, light).also { autopilot.start(it) }

        assertEquals(BuilderAnswer.Configure(lighting, BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.toggleFace(0, 2, 1), lighting))
    }

    @Test
    fun `a list's first line opens the search that stays open, over the steps before and the step's input`() {
        val answer = click(player(chop, light), BuilderWidgets.listAdd(0), lighting.copy(typing = Typing("amount")))

        val context = OptionContext(settings = light, before = FlowContext(), input = InputSource.EARLIER_STEPS)
        assertEquals(BuilderAnswer.Several(lighting, LOGS_LIST, context), answer)
    }

    @Test
    fun `a list's first line where the kind has no list does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, light), BuilderWidgets.listAdd(1), lighting))
    }

    @Test
    fun `no search opens over a list while the flow runs`() {
        val player = player(chop, light).also { autopilot.start(it) }

        assertEquals(BuilderAnswer.Configure(lighting, BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.listAdd(0), lighting))
    }

    @Test
    fun `a line's x takes its item out`() {
        val two = lighting.with("logs", "1511,1521")

        assertEquals(BuilderAnswer.Configure(lighting.with("logs", "1521")), click(player(chop, light), BuilderWidgets.lineRemoveFace(0, 0), two))
    }

    @Test
    fun `an x past the list's items does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, light), BuilderWidgets.lineRemoveFace(0, 1), lighting))
    }

    @Test
    fun `an x in a list the kind does not have does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, light), BuilderWidgets.lineRemoveFace(1, 0), lighting))
    }

    @Test
    fun `nothing leaves a list while the flow runs`() {
        val player = player(chop, light).also { autopilot.start(it) }

        assertEquals(BuilderAnswer.Configure(lighting, BuilderScreen.STOP_FIRST), click(player, BuilderWidgets.lineRemoveFace(0, 0), lighting))
    }

    @Test
    fun `a row clicked in the search that stays open adds its item`() {
        assertEquals(BuilderAnswer.Configure(lighting.with("logs", "1511,1521")), screen.toggledItem(lighting, lighting, "logs", "1521"))
    }

    @Test
    fun `a row clicked again takes its item out`() {
        assertEquals(BuilderAnswer.Configure(lighting.with("logs", "")), screen.toggledItem(lighting, lighting, "logs", "1511"))
    }

    @Test
    fun `a full list takes no more items`() {
        val full = lighting.with("logs", (1..28).joinToString(","))

        assertEquals(BuilderAnswer.Configure(full, "Autopilot: the list holds 28 items."), screen.toggledItem(full, full, "logs", "1521"))
    }

    @Test
    fun `a full list still lets an item out`() {
        val full = lighting.with("logs", (1..28).joinToString(","))

        assertEquals(BuilderAnswer.Configure(full.with("logs", (2..28).joinToString(","))), screen.toggledItem(full, full, "logs", "1"))
    }

    @Test
    fun `a row clicked for another step changes nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.toggledItem(choppingDraft, lighting, "logs", "1521"))
    }

    @Test
    fun `a row clicked once the screen closed changes nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.toggledItem(null, lighting, "logs", "1521"))
    }

    @Test
    fun `a row naming no item changes nothing`() {
        assertEquals(BuilderAnswer.Ignored, screen.toggledItem(lighting, lighting, "logs", "nearest"))
    }

    private val stocking = StepDraft(1, StepSettings("stock", mapOf("withdraw" to "1511:14,1521")), new = false)
    private val stock = StepSettings("stock", mapOf("withdraw" to "1511:14,1521"))

    @Test
    fun `a withdrawal's amount box opens Enter amount for its line`() {
        assertEquals(BuilderAnswer.Amount(stocking.copy(typing = Typing("withdraw", 1521))), click(player(chop, stock), BuilderWidgets.lineAmountFace(0, 1), stocking))
    }

    @Test
    fun `a withdrawal's All button puts its amount back to as many as fit`() {
        assertEquals(BuilderAnswer.Configure(stocking.with("withdraw", "1511,1521")), click(player(chop, stock), BuilderWidgets.lineAllFace(0, 0), stocking))
    }

    @Test
    fun `a list without amounts has no amount box or All button to click`() {
        assertEquals(
            listOf(BuilderAnswer.Ignored, BuilderAnswer.Ignored),
            listOf(click(player(chop, light), BuilderWidgets.lineAmountFace(0, 0), lighting), click(player(chop, light), BuilderWidgets.lineAllFace(0, 0), lighting)),
        )
    }

    @Test
    fun `an amount box past the list's items does nothing`() {
        assertEquals(BuilderAnswer.Ignored, click(player(chop, stock), BuilderWidgets.lineAmountFace(0, 2), stocking))
    }

    @Test
    fun `no amount changes while the flow runs`() {
        val player = player(chop, stock).also { autopilot.start(it) }

        assertEquals(
            listOf(BuilderAnswer.Configure(stocking, BuilderScreen.STOP_FIRST), BuilderAnswer.Configure(stocking, BuilderScreen.STOP_FIRST)),
            listOf(click(player, BuilderWidgets.lineAmountFace(0, 0), stocking), click(player, BuilderWidgets.lineAllFace(0, 0), stocking)),
        )
    }

    @Test
    fun `a number typed for a withdrawal sets its amount`() {
        val answer = screen.typed(stocking.copy(typing = Typing("withdraw", 1521)), 150000)

        assertEquals(BuilderAnswer.Configure(stocking.with("withdraw", "1511:14,1521:150000")), answer)
    }

    @Test
    fun `a withdrawal of none says the amount's rule`() {
        val answer = screen.typed(stocking.copy(typing = Typing("withdraw", 1521)), 0)

        assertEquals(BuilderAnswer.Configure(stocking, "Autopilot: the amount takes 1 or more."), answer)
    }

    @Test
    fun `a typed number for a field the step no longer has is ignored`() {
        assertEquals(BuilderAnswer.Ignored, screen.typed(choppingDraft.copy(typing = Typing("tile")), 5))
    }

    @Test
    fun `a click on a field the settings hide does nothing`() {
        val banking = StepDraft(1, StepSettings("bank"), new = false)

        assertEquals(BuilderAnswer.Ignored, click(player(chop, StepSettings("bank")), BuilderWidgets.listAdd(1), banking))
    }
}
