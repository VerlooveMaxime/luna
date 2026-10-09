package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.FlowResolver
import game.idle.flow.ResolvedStep
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.StepTypes
import game.idle.flow.option.FakeNames
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuilderOverviewTest {

    /** A kind with a target: a tree picked by name, oak showing its logs; refused without a tree. */
    private object Chop : StepType {
        override val kind = "chop"
        override val label = "chop"
        override val fields = emptyList<StepField>()

        override fun summary(settings: StepSettings): String = "chop"

        override fun icon(settings: StepSettings): StepIcon = StepIcon.Media("axe", 0)

        override fun target(names: GameNames): StepTarget =
            StepTarget("tree", OptionSource { listOf(StepOption("oak", names.item(1521), OptionIcon.Item(1521))) })

        override fun details(settings: StepSettings, context: FlowContext): List<String> =
            listOf("5 per lap") + context.stepsGathering(setOf(1)).map { "after step $it" }

        override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
            settings["tree"] ?: throw FlowError("chop needs a tree")
            return FakeStepType("chop").resolve(StepSettings("chop"), context)
        }
    }

    private val font = ClientFont(IntArray(256) { 5 })
    private val types = StepTypes(listOf(Chop, FakeStepType("drop")))
    private val overview = BuilderOverview(FlowResolver(types), FakeNames(items = mapOf(1521 to "Oak")), font)

    private val oak = StepSettings("chop", mapOf("tree" to "oak"))
    private val drop = StepSettings("drop")

    private fun slot(state: IdleState, slot: Int = 0): SlotView = overview.slots(state, slots = 4)[slot]

    private fun step(state: IdleState, slot: Int = 0): SlotView.Step = slot(state, slot) as SlotView.Step

    private fun updates(state: IdleState): Map<Int, WidgetUpdate> = overview.updates(state, slots = 4).associateBy {
        when (it) {
            is WidgetUpdate.Text -> it.id
            is WidgetUpdate.Picture -> it.id
            is WidgetUpdate.Visible -> it.id
            is WidgetUpdate.Colour -> it.id
        }
    }

    @Test
    fun `a slot shows each step slot the player has`() {
        assertEquals(4, overview.slots(IdleState(), slots = 4).size)
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
            listOf("@gry@not set yet", "5 per lap", "@red@! chop needs a tree"),
            step(IdleState(steps = listOf(StepSettings("chop")))).lines,
        )
    }

    @Test
    fun `a long reason takes up to three red lines, the details giving way`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: you need an axe that you have the level to use.")

        assertEquals(
            listOf("Oak", "@red@! You need an axe", "@red@that you have the", "@red@level to use."),
            step(state).lines,
        )
    }

    @Test
    fun `a reason longer than three lines is cut on the third`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: " + "word ".repeat(20))

        assertEquals("@red@word word word word..", step(state).lines.last())
    }

    @Test
    fun `the running step is framed green`() {
        assertEquals(BuilderWidgets.RUNNING, step(IdleState(steps = listOf(oak), running = true)).frame)
    }

    @Test
    fun `a running step that is blocked is framed red`() {
        assertEquals(BuilderWidgets.PROBLEM, step(IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: no axe.")).frame)
    }

    @Test
    fun `a block belongs to the running step only`() {
        val state = IdleState(steps = listOf(oak, oak), stepIndex = 1, running = true, blocked = "Autopilot: no axe.")

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
            listOf("@gry@Run", "@gry@Stop", "@gry@Clear"),
            listOf(BuilderWidgets.RUN, BuilderWidgets.STOP, BuilderWidgets.CLEAR).map { (sent[it] as WidgetUpdate.Text).text },
        )
    }

    @Test
    fun `a stopped flow can be run and cleared`() {
        val sent = updates(IdleState(steps = listOf(oak)))

        assertEquals(listOf("Run", "Clear"), listOf(BuilderWidgets.RUN, BuilderWidgets.CLEAR).map { (sent[it] as WidgetUpdate.Text).text })
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
                    WidgetUpdate.Visible(BuilderWidgets.kindButton(2), visible = false),
                    WidgetUpdate.Visible(BuilderWidgets.kindButton(BuilderWidgets.KIND_BUTTONS - 1), visible = false),
                ),
            ),
        )
    }

    @Test
    fun `a page shows its layer and hides the other`() {
        assertEquals(
            listOf(WidgetUpdate.Visible(BuilderWidgets.OVERVIEW, visible = false), WidgetUpdate.Visible(BuilderWidgets.KINDS, visible = true)),
            overview.page(BuilderPage.KINDS),
        )
    }
}
