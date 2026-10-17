package game.idle.ui

import game.idle.flow.StopSettings
import game.idle.flow.RunProgress
import game.idle.IdleState
import game.idle.SavedFlow
import game.idle.flow.FakeStepType
import game.idle.flow.StepDangers
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.ResolvedStep
import game.idle.flow.SavedFlows
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.StepTypes
import game.idle.flow.option.FakeNames
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget
import game.idle.font.ClientFont
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuilderOverviewTest {

    /** A kind with a target: a tree picked by name, oak showing its logs; refused without a tree. */
    private object Chop : StepType {
        override val kind = "chop"
        override val label = "chop"
        override val description = "Cuts trees."

        override fun fields(names: GameNames): List<StepField> = emptyList()

        override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("axe", 0)

        override fun target(names: GameNames): StepTarget =
            StepTarget("tree", OptionSource { listOf(StepOption("oak", names.item(1521), OptionIcon.Item(1521))) })

        override fun details(settings: StepSettings, context: FlowContext): List<String> =
            listOf("5 per lap") + context.stepsGathering(setOf(1)).map { "after step $it" }

        override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
            settings["tree"] ?: throw FlowError("Needs a tree")
            return FakeStepType("chop").resolve(StepSettings("chop"), context)
        }
    }

    /** A chop that takes an Until goal. */
    private object GoalChop : StepType by Chop {
        override val kind = "goalchop"
        override val goal = true
    }

    private val font = ClientFont(IntArray(256) { 5 })
    private val types = StepTypes(listOf(Chop, FakeStepType("drop"), GoalChop))
    private val resolver = FlowResolver(types)
    private val overview = BuilderOverview(resolver, REFLEXES, REFLEX_FORM, FakeNames(items = mapOf(1521 to "Oak")), font, SavedFlows(2), StepDangers(resolver, DANGERS, FakeNames()))

    private val oak = StepSettings("chop", mapOf("tree" to "oak"))
    private val drop = StepSettings("drop")

    /** A new character standing where no aggressive npc reaches. */
    private val safe = OptionFacts(here = Tile(3200, 3200))

    /** A new character standing where a dark wizard reaches. */
    private val endangered = OptionFacts(here = DANGEROUS)

    private fun slot(state: IdleState, slot: Int = 0, facts: OptionFacts = safe): SlotView = overview.slots(state, slots = 4, facts)[slot]

    private fun step(state: IdleState, slot: Int = 0, facts: OptionFacts = safe): SlotView.Step = slot(state, slot, facts) as SlotView.Step

    private fun updates(state: IdleState): Map<Int, WidgetUpdate> = overview.updates(state, slots = 4, reflexSlots = 2, safe).associateBy {
        when (it) {
            is WidgetUpdate.Text -> it.id
            is WidgetUpdate.Picture -> it.id
            is WidgetUpdate.Visible -> it.id
            is WidgetUpdate.Colour -> it.id
            is WidgetUpdate.Placement -> placementKey(it.list)
        }
    }

    @Test
    fun `a slot shows each step slot the player has`() {
        assertEquals(4, overview.slots(IdleState(), slots = 4, safe).size)
    }

    @Test
    fun `the slot after the last step adds one`() {
        assertEquals(SlotView.Add, slot(IdleState(steps = listOf(oak)), slot = 1))
    }

    @Test
    fun `the slots after it are free`() {
        assertEquals(SlotView.Free, slot(IdleState(steps = listOf(oak)), slot = 2))
    }

    @Test
    fun `a step shows its number and its kind with a capital`() {
        val step = step(IdleState(steps = listOf(oak)))

        assertEquals(listOf<Any>(1, "Chop"), listOf(step.number, step.kind))
    }

    @Test
    fun `a picked target shows its picture with the kind's icon in the corner`() {
        val step = step(IdleState(steps = listOf(oak)))

        assertEquals(listOf(WidgetPicture.Item(1521), WidgetPicture.Media("axe", 0)), listOf(step.picture, step.corner))
    }

    @Test
    fun `a picked target leads the lines, then the kind's details`() {
        assertEquals(listOf("Oak", "5 per lap"), step(IdleState(steps = listOf(oak))).lines)
    }

    @Test
    fun `a step's details know what the steps before it set up`() {
        val state = IdleState(steps = listOf(step("drop", "gather"), oak))

        assertEquals(listOf("Oak", "5 per lap", "after step 1"), step(state, slot = 1).lines)
    }

    @Test
    fun `nothing picked shows the kind's icon alone`() {
        val step = step(IdleState(steps = listOf(StepSettings("chop"))))

        assertEquals(listOf(WidgetPicture.Media("axe", 0), WidgetPicture.None), listOf(step.picture, step.corner))
    }

    @Test
    fun `nothing picked says so in grey`() {
        assertEquals("@gry@not set yet", step(IdleState(steps = listOf(StepSettings("chop", mapOf("within" to "5"))))).lines.first())
    }

    @Test
    fun `a value no option has shows as it is`() {
        val palm = StepSettings("chop", mapOf("tree" to "palm"))

        assertEquals("palm", step(IdleState(steps = listOf(palm))).lines.first())
    }

    @Test
    fun `a long line is cut to the slot's width`() {
        val long = StepSettings("chop", mapOf("tree" to "x".repeat(30)))

        assertEquals("x".repeat(19) + "..", step(IdleState(steps = listOf(long))).lines.first())
    }

    @Test
    fun `a kind without a target shows its details alone`() {
        assertEquals(listOf<String>(), step(IdleState(steps = listOf(drop))).lines)
    }

    @Test
    fun `a step that cannot work is framed red`() {
        assertEquals(BuilderWidgets.PROBLEM, step(IdleState(steps = listOf(StepSettings("chop")))).frame)
    }

    @Test
    fun `its reason takes the last line in red`() {
        assertEquals(
            listOf("@gry@not set yet", "5 per lap", "@red@! Needs a tree"),
            step(IdleState(steps = listOf(StepSettings("chop")))).lines,
        )
    }

    @Test
    fun `a long reason takes up to three red lines, the details giving way`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Needs an axe that you have the level to use")

        assertEquals(
            listOf("Oak", "@red@! Needs an axe that", "@red@you have the level to", "@red@use"),
            step(state).lines,
        )
    }

    @Test
    fun `a reason longer than three lines is cut on the third`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "word ".repeat(20).trim())

        assertEquals("@red@word word word word..", step(state).lines.last())
    }

    @Test
    fun `what the running step waits for takes the last line in yellow, the frame still green`() {
        val state = IdleState(steps = listOf(oak), running = true, waiting = "No tree in reach")

        assertEquals(
            listOf("Oak", "5 per lap", "@yel@! No tree in reach") to BuilderWidgets.RUNNING,
            step(state).lines to step(state).frame,
        )
    }

    @Test
    fun `a wait belongs to the running step only`() {
        val state = IdleState(steps = listOf(oak, oak), stepIndex = 1, running = true, waiting = "No tree in reach")

        assertEquals(listOf("Oak", "5 per lap"), step(state, slot = 0).lines)
    }

    @Test
    fun `what the running step does on its way takes the last line in white, with no mark`() {
        val state = IdleState(steps = listOf(oak), running = true, underWay = "Opening the door")

        assertEquals(listOf("Oak", "5 per lap", "@whi@Opening the door"), step(state).lines)
    }

    @Test
    fun `what a step does on its way comes after its wait`() {
        val state = IdleState(steps = listOf(oak), running = true, waiting = "No tree in reach", underWay = "Opening the door")

        assertEquals(listOf("@yel@! No tree in reach", "@whi@Opening the door"), step(state).lines.drop(2))
    }

    @Test
    fun `what a step does on its way belongs to the running step only`() {
        val state = IdleState(steps = listOf(oak, oak), stepIndex = 1, running = true, underWay = "Opening the door")

        assertEquals(listOf("Oak", "5 per lap"), step(state, slot = 0).lines)
    }

    @Test
    fun `an aggressive npc in reach of where a stopped step works takes its last lines in yellow, the frame unchanged`() {
        val step = step(IdleState(steps = listOf(oak)), facts = endangered)

        assertEquals(
            listOf("Oak", "5 per lap", "@yel@! Dark wizard, level", "@yel@7, attacks below 15") to BuilderWidgets.EDGE,
            step.lines to step.frame,
        )
    }

    @Test
    fun `a danger comes after the reason and never pushes out the target`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Needs an axe that you have the level to use")

        assertEquals(
            listOf("Oak", "@red@! Needs an axe that", "@red@you have the level to", "@red@use"),
            step(state, facts = endangered).lines,
        )
    }

    @Test
    fun `a danger follows the running step's wait`() {
        val state = IdleState(steps = listOf(oak), running = true, waiting = "No tree in reach")

        assertEquals(
            listOf("Oak", "@yel@! No tree in reach", "@yel@! Dark wizard, level", "@yel@7, attacks below 15"),
            step(state, facts = endangered).lines,
        )
    }

    @Test
    fun `a danger shows whole or not at all, so a second one under a long first waits for the band`() {
        val state = IdleState(steps = listOf(oak, oak), running = true, stepIndex = 1, waiting = "No tree in reach for now")

        assertEquals(
            listOf("Oak", "5 per lap", "@yel@! No tree in reach", "@yel@for now"),
            step(state, slot = 1, facts = endangered).lines,
        )
    }

    @Test
    fun `a step that cannot work shows its reason, not the dangers`() {
        assertEquals(
            listOf("@gry@not set yet", "5 per lap", "@red@! Needs a tree"),
            step(IdleState(steps = listOf(StepSettings("chop"))), facts = endangered).lines,
        )
    }

    @Test
    fun `a player the npc leaves alone sees no danger`() {
        assertEquals(listOf("Oak", "5 per lap"), step(IdleState(steps = listOf(oak)), facts = endangered.copy(combat = 15)).lines)
    }

    @Test
    fun `the running step is framed green`() {
        assertEquals(BuilderWidgets.RUNNING, step(IdleState(steps = listOf(oak), running = true)).frame)
    }

    @Test
    fun `a running step that is blocked is framed red`() {
        assertEquals(BuilderWidgets.PROBLEM, step(IdleState(steps = listOf(oak), running = true, blocked = "Needs an axe")).frame)
    }

    @Test
    fun `a block belongs to the running step only`() {
        val state = IdleState(steps = listOf(oak, oak), stepIndex = 1, running = true, blocked = "Needs an axe")

        assertEquals(BuilderWidgets.EDGE, step(state, slot = 0).frame)
    }

    @Test
    fun `a stopped step that can work has the tile's edge`() {
        assertEquals(BuilderWidgets.EDGE, step(IdleState(steps = listOf(oak))).frame)
    }

    @Test
    fun `a step of a kind that no longer exists shows its kind and why`() {
        val step = step(IdleState(steps = listOf(StepSettings("dig"))))

        assertEquals(listOf<Any>("Dig", WidgetPicture.None, listOf("@red@! 'dig' is not a kind", "@red@of step")), listOf(step.kind, step.picture, step.lines))
    }

    @Test
    fun `stopped, the status says so and counts the steps`() {
        assertEquals("Stopped.  @gry@1 of 4 steps", overview.status(IdleState(steps = listOf(oak)), slots = 4))
    }

    @Test
    fun `running, the status names the step and the lap`() {
        val state = IdleState(steps = listOf(oak, drop), stepIndex = 1, running = true, laps = 2)

        assertEquals("Running step 2 of 2, lap 3.  @gry@2 of 4 steps", overview.status(state, slots = 4))
    }

    @Test
    fun `a step's slot sends its texts and pictures`() {
        val sent = updates(IdleState(steps = listOf(oak)))

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.slotNumber(0), "1"),
                WidgetUpdate.Picture(BuilderWidgets.slotPicture(0), WidgetPicture.Item(1521)),
                WidgetUpdate.Visible(BuilderWidgets.slotCornerLayer(0), visible = true),
                WidgetUpdate.Text(BuilderWidgets.slotLine(0, 3), ""),
                WidgetUpdate.Colour(BuilderWidgets.slotFrame(0), BuilderWidgets.EDGE),
            ),
            listOf(
                sent[BuilderWidgets.slotNumber(0)], sent[BuilderWidgets.slotPicture(0)], sent[BuilderWidgets.slotCornerLayer(0)],
                sent[BuilderWidgets.slotLine(0, 3)], sent[BuilderWidgets.slotFrame(0)],
            ),
        )
    }

    @Test
    fun `the corner's box hides with no corner`() {
        assertEquals(WidgetUpdate.Visible(BuilderWidgets.slotCornerLayer(0), visible = false), updates(IdleState(steps = listOf(drop)))[BuilderWidgets.slotCornerLayer(0)])
    }

    @Test
    fun `the add slot shows a plus and its words`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(WidgetUpdate.Text(BuilderWidgets.slotPlus(0), "+"), WidgetUpdate.Text(BuilderWidgets.slotAdd(0), "Add step")),
            listOf(sent[BuilderWidgets.slotPlus(0)], sent[BuilderWidgets.slotAdd(0)]),
        )
    }

    @Test
    fun `the add slot keeps the tile's edge`() {
        assertEquals(WidgetUpdate.Colour(BuilderWidgets.slotFrame(0), BuilderWidgets.EDGE), updates(IdleState())[BuilderWidgets.slotFrame(0)])
    }

    @Test
    fun `a free slot is blank and has no edge`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.slotNumber(1), ""),
                WidgetUpdate.Picture(BuilderWidgets.slotPicture(1), WidgetPicture.None),
                WidgetUpdate.Text(BuilderWidgets.slotKind(1), ""),
                WidgetUpdate.Text(BuilderWidgets.slotAdd(1), ""),
                WidgetUpdate.Colour(BuilderWidgets.slotFrame(1), BuilderWidgets.FREE),
            ),
            listOf(
                sent[BuilderWidgets.slotNumber(1)], sent[BuilderWidgets.slotPicture(1)], sent[BuilderWidgets.slotKind(1)],
                sent[BuilderWidgets.slotAdd(1)], sent[BuilderWidgets.slotFrame(1)],
            ),
        )
    }

    @Test
    fun `base levels are lit by default`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.BASE_LEVELS, "@whi@Base"),
                WidgetUpdate.Colour(BuilderWidgets.BASE_LEVELS_FRAME, BuilderWidgets.LIT),
                WidgetUpdate.Text(BuilderWidgets.BOOSTED_LEVELS, "Boosted"),
                WidgetUpdate.Colour(BuilderWidgets.BOOSTED_LEVELS_FRAME, BuilderWidgets.UNLIT),
            ),
            listOf(
                sent[BuilderWidgets.BASE_LEVELS], sent[BuilderWidgets.BASE_LEVELS_FRAME], sent[BuilderWidgets.BOOSTED_LEVELS],
                sent[BuilderWidgets.BOOSTED_LEVELS_FRAME],
            ),
        )
    }

    @Test
    fun `boosted levels light when counted`() {
        val sent = updates(IdleState(countBoostedLevels = true))

        assertEquals(
            listOf(WidgetUpdate.Text(BuilderWidgets.BOOSTED_LEVELS, "@whi@Boosted"), WidgetUpdate.Colour(BuilderWidgets.BASE_LEVELS_FRAME, BuilderWidgets.UNLIT)),
            listOf(sent[BuilderWidgets.BOOSTED_LEVELS], sent[BuilderWidgets.BASE_LEVELS_FRAME]),
        )
    }

    @Test
    fun `an empty flow greys every button`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf("@gry@Save", "@gry@Run", "@gry@Stop", "@gry@Clear"),
            listOf(BuilderWidgets.SAVE_FLOW, BuilderWidgets.RUN, BuilderWidgets.STOP, BuilderWidgets.CLEAR).map { (sent[it] as WidgetUpdate.Text).text },
        )
    }

    @Test
    fun `a stopped flow can be run and cleared`() {
        val sent = updates(IdleState(steps = listOf(oak)))

        assertEquals(listOf("Run", "Clear"), listOf(BuilderWidgets.RUN, BuilderWidgets.CLEAR).map { (sent[it] as WidgetUpdate.Text).text })
    }

    @Test
    fun `a flow with steps can be saved, running or not`() {
        val sent = updates(IdleState(steps = listOf(oak), running = true))

        assertEquals(WidgetUpdate.Text(BuilderWidgets.SAVE_FLOW, "Save"), sent[BuilderWidgets.SAVE_FLOW])
    }

    @Test
    fun `a flow never saved leaves the title plain`() {
        assertEquals(WidgetUpdate.Text(BuilderWidgets.TITLE, "Flow builder"), updates(IdleState(steps = listOf(oak)))[BuilderWidgets.TITLE])
    }

    @Test
    fun `the title names the saved flow the builder holds, changed or not`() {
        val state = IdleState(steps = listOf(oak), savedFlows = listOf(SavedFlow(0, "Willows", emptyList())), savedSlot = 0)

        assertEquals("Flow builder: Willows (changed)", overview.title(state))
    }

    @Test
    fun `a running flow can be stopped but not cleared`() {
        val sent = updates(IdleState(steps = listOf(oak), running = true))

        assertEquals(listOf("Stop", "@gry@Clear"), listOf(BuilderWidgets.STOP, BuilderWidgets.CLEAR).map { (sent[it] as WidgetUpdate.Text).text })
    }

    @Test
    fun `the status goes with the overview`() {
        assertEquals(WidgetUpdate.Text(BuilderWidgets.STATUS, "Stopped.  @gry@0 of 4 steps"), updates(IdleState())[BuilderWidgets.STATUS])
    }

    @Test
    fun `the kind picker asks what the next step does`() {
        assertEquals(WidgetUpdate.Text(BuilderWidgets.KINDS_TITLE, "What should step 2 do?"), overview.kinds(IdleState(steps = listOf(oak))).first())
    }

    @Test
    fun `a kind's button shows its icon and its name`() {
        val sent = overview.kinds(IdleState())

        assertEquals(
            true,
            sent.containsAll(
                listOf(
                    WidgetUpdate.Visible(BuilderWidgets.kindButton(1), visible = true),
                    WidgetUpdate.Picture(BuilderWidgets.kindPicture(1), WidgetPicture.Media("drop", 0)),
                    WidgetUpdate.Text(BuilderWidgets.kindLabel(1), "Drop label"),
                ),
            ),
        )
    }

    @Test
    fun `buttons past the kinds are hidden`() {
        assertEquals(
            true,
            overview.kinds(IdleState()).containsAll(
                listOf(
                    WidgetUpdate.Visible(BuilderWidgets.kindButton(3), visible = false),
                    WidgetUpdate.Visible(BuilderWidgets.kindButton(BuilderWidgets.KIND_BUTTONS - 1), visible = false),
                ),
            ),
        )
    }

    @Test
    fun `a page shows its layer and hides the others`() {
        val visible = overview.page(BuilderPage.CONFIGURE).filterIsInstance<WidgetUpdate.Visible>().filter { it.id in SCREENS }

        assertEquals(
            listOf(
                WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = false),
                WidgetUpdate.Visible(BuilderWidgets.KINDS, visible = false),
                WidgetUpdate.Visible(BuilderWidgets.CONFIGURE, visible = true),
            ),
            visible,
        )
    }

    @Test
    fun `the Steps tab shows the overview with its slots, the tab lit`() {
        val page = overview.page(BuilderPage.OVERVIEW)

        assertEquals(
            listOf(
                WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = true),
                WidgetUpdate.Visible(BuilderWidgets.SLOTS, visible = true),
                WidgetUpdate.Visible(BuilderWidgets.REFLEX_ROWS, visible = false),
                WidgetUpdate.Text(BuilderWidgets.STEPS_TAB, "@whi@Steps"),
                WidgetUpdate.Colour(BuilderWidgets.STEPS_TAB_FRAME, BuilderWidgets.LIT),
                WidgetUpdate.Text(BuilderWidgets.REFLEXES_TAB, "Reflexes"),
                WidgetUpdate.Colour(BuilderWidgets.REFLEXES_TAB_FRAME, BuilderWidgets.UNLIT),
            ),
            page.take(7),
        )
    }

    @Test
    fun `the Reflexes tab shows the overview with its reflex rows instead of the slots, the tab lit`() {
        val page = overview.page(BuilderPage.REFLEXES)

        assertEquals(
            listOf(
                WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = true),
                WidgetUpdate.Visible(BuilderWidgets.SLOTS, visible = false),
                WidgetUpdate.Visible(BuilderWidgets.REFLEX_ROWS, visible = true),
                WidgetUpdate.Text(BuilderWidgets.STEPS_TAB, "Steps"),
                WidgetUpdate.Colour(BuilderWidgets.STEPS_TAB_FRAME, BuilderWidgets.UNLIT),
                WidgetUpdate.Text(BuilderWidgets.REFLEXES_TAB, "@whi@Reflexes"),
                WidgetUpdate.Colour(BuilderWidgets.REFLEXES_TAB_FRAME, BuilderWidgets.LIT),
            ),
            page.take(7),
        )
    }

    @Test
    fun `the Steps tab shows Attach all reflexes and the Reflexes tab hides it`() {
        assertEquals(
            listOf(true, false),
            listOf(BuilderPage.OVERVIEW, BuilderPage.REFLEXES).map { page ->
                overview.page(page).filterIsInstance<WidgetUpdate.Visible>().single { it.id == BuilderWidgets.ATTACH_ALL_REFLEXES_LAYER }.visible
            },
        )
    }

    private fun attachAll(state: IdleState): String = (updates(state).getValue(BuilderWidgets.ATTACH_ALL_REFLEXES) as WidgetUpdate.Text).text

    @Test
    fun `a stopped flow of steps and reflexes can attach all reflexes`() {
        assertEquals("Attach all reflexes", attachAll(IdleState(steps = listOf(oak), reflexes = listOf(trout))))
    }

    @Test
    fun `attach all reflexes is greyed while running, without a step or without a reflex`() {
        assertEquals(
            List(3) { "@gry@Attach all reflexes" },
            listOf(
                attachAll(IdleState(steps = listOf(oak), reflexes = listOf(trout), running = true)),
                attachAll(IdleState(reflexes = listOf(trout))),
                attachAll(IdleState(steps = listOf(oak))),
            ),
        )
    }

    private val trout = ReflexSettings(1, mapOf("foods" to "333"))

    private fun rowTexts(state: IdleState, row: Int): List<Any> {
        val sent = updates(state)
        return listOf(
            (sent.getValue(BuilderWidgets.reflexRowNumber(row)) as WidgetUpdate.Text).text,
            (sent.getValue(BuilderWidgets.reflexRowPicture(row)) as WidgetUpdate.Picture).picture,
            (sent.getValue(BuilderWidgets.reflexRowText(row)) as WidgetUpdate.Text).text,
            (sent.getValue(BuilderWidgets.reflexRowFrame(row)) as WidgetUpdate.Colour).rgb,
        )
    }

    @Test
    fun `the Reflexes tab has a row per reflex slot`() {
        assertEquals(3, overview.reflexRows(IdleState(), reflexSlots = 3).size)
    }

    @Test
    fun `a reflex's row shows its number, picture and sentence`() {
        assertEquals(
            ReflexRowView.Reflex(1, WidgetPicture.Item(333), "Hitpoints below 50%: eat trout", BuilderWidgets.EDGE),
            overview.reflexRows(IdleState(reflexes = listOf(trout)), reflexSlots = 2)[0],
        )
    }

    @Test
    fun `the row after the last reflex adds one, the rows past it are free`() {
        assertEquals(
            listOf(ReflexRowView.Add, ReflexRowView.Free),
            overview.reflexRows(IdleState(), reflexSlots = 2),
        )
    }

    @Test
    fun `a reflex that cannot work shows in red`() {
        val deleted = ReflexSettings(1, mapOf("do" to "jump", "step" to "9"))

        val row = overview.reflexRows(IdleState(reflexes = listOf(deleted)), reflexSlots = 1)[0] as ReflexRowView.Reflex

        assertEquals(listOf("@red@Hitpoints below 50%: jump to a deleted step", BuilderWidgets.PROBLEM), listOf(row.sentence, row.frame))
    }

    @Test
    fun `a reflex row's widgets show the reflex`() {
        assertEquals(listOf("1", WidgetPicture.Item(333), "Hitpoints below 50%: eat trout", BuilderWidgets.EDGE), rowTexts(IdleState(reflexes = listOf(trout)), 0))
    }

    @Test
    fun `the adding row says so and the free rows show nothing`() {
        val state = IdleState()

        assertEquals(
            listOf(listOf("", WidgetPicture.None, "@gry@+ Add reflex", BuilderWidgets.EDGE), listOf("", WidgetPicture.None, "", BuilderWidgets.FREE)),
            listOf(rowTexts(state, 0), rowTexts(state, 1)),
        )
    }

    @Test
    fun `a flow of reflexes alone can be cleared`() {
        assertEquals("Clear", (updates(IdleState(reflexes = listOf(trout))).getValue(BuilderWidgets.CLEAR) as WidgetUpdate.Text).text)
    }

    private companion object {
        val SCREENS = setOf(BuilderWidgets.OVERVIEW, BuilderWidgets.KINDS, BuilderWidgets.CONFIGURE)
    }

    @Test
    fun `the Flow stops row lights Never by default and hides the number box`() {
        val sent = updates(IdleState())

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.stopChoice(0), "@whi@Never"),
                WidgetUpdate.Colour(BuilderWidgets.stopFrame(0), BuilderWidgets.LIT),
                WidgetUpdate.Text(BuilderWidgets.stopChoice(1), "After laps"),
                WidgetUpdate.Colour(BuilderWidgets.stopFrame(1), BuilderWidgets.UNLIT),
                WidgetUpdate.Text(BuilderWidgets.stopChoice(3), "When all goals are met"),
                WidgetUpdate.Visible(BuilderWidgets.STOPS_AFTER, visible = false),
            ),
            listOf(
                sent[BuilderWidgets.stopChoice(0)], sent[BuilderWidgets.stopFrame(0)], sent[BuilderWidgets.stopChoice(1)], sent[BuilderWidgets.stopFrame(1)],
                sent[BuilderWidgets.stopChoice(3)], sent[BuilderWidgets.STOPS_AFTER],
            ),
        )
    }

    @Test
    fun `a stop after laps lights its choice and shows the laps in the box`() {
        val sent = updates(IdleState(stop = StopSettings("laps", "5")))

        assertEquals(
            listOf(
                WidgetUpdate.Text(BuilderWidgets.stopChoice(1), "@whi@After laps"),
                WidgetUpdate.Colour(BuilderWidgets.stopFrame(1), BuilderWidgets.LIT),
                WidgetUpdate.Colour(BuilderWidgets.stopFrame(0), BuilderWidgets.UNLIT),
                WidgetUpdate.Visible(BuilderWidgets.STOPS_AFTER, visible = true),
                WidgetUpdate.Text(BuilderWidgets.STOPS_AFTER_TEXT, "5 laps"),
            ),
            listOf(sent[BuilderWidgets.stopChoice(1)], sent[BuilderWidgets.stopFrame(1)], sent[BuilderWidgets.stopFrame(0)], sent[BuilderWidgets.STOPS_AFTER], sent[BuilderWidgets.STOPS_AFTER_TEXT]),
        )
    }

    @Test
    fun `a stop after time shows the minutes in the box`() {
        assertEquals(WidgetUpdate.Text(BuilderWidgets.STOPS_AFTER_TEXT, "30 min"), updates(IdleState(stop = StopSettings("time", "30")))[BuilderWidgets.STOPS_AFTER_TEXT])
    }

    @Test
    fun `a stop with no number typed yet says so in the box`() {
        val sent = updates(IdleState(stop = StopSettings("time", "")))

        assertEquals(
            listOf(WidgetUpdate.Visible(BuilderWidgets.STOPS_AFTER, visible = true), WidgetUpdate.Text(BuilderWidgets.STOPS_AFTER_TEXT, "@gry@not set")),
            listOf(sent[BuilderWidgets.STOPS_AFTER], sent[BuilderWidgets.STOPS_AFTER_TEXT]),
        )
    }

    private val oaksTo20 = StepSettings("goalchop", mapOf("tree" to "oak", "goal" to "level", "goalSkill" to "strength", "goalLevel" to "20"))

    @Test
    fun `a step's goal is its last line`() {
        assertEquals(listOf("Oak", "5 per lap", "until Strength 20"), step(IdleState(steps = listOf(oaksTo20))).lines)
    }

    @Test
    fun `a goal met shows in green`() {
        val state = IdleState(steps = listOf(oaksTo20), progress = RunProgress(met = setOf(0)))

        assertEquals(listOf("Oak", "5 per lap", "@gre@Goal met: Strength 20"), step(state).lines)
    }

    @Test
    fun `a goal too long for one line wraps onto two, both green once met`() {
        val long = oaksTo20.with("goalSkill", "runecrafting").with("goalLevel", "45")
        val state = IdleState(steps = listOf(long), progress = RunProgress(met = setOf(0)))

        assertEquals(listOf("Oak", "5 per lap", "@gre@Goal met:", "@gre@Runecrafting 45"), step(state).lines)
    }

    @Test
    fun `another step's goal met leaves this one's line plain`() {
        val state = IdleState(steps = listOf(oaksTo20, oaksTo20), progress = RunProgress(met = setOf(1)))

        assertEquals("until Strength 20", step(state).lines.last())
    }
}
