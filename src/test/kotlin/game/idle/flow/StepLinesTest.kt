package game.idle.flow

import org.junit.jupiter.api.Assertions.assertNull
import io.luna.game.model.mob.Skill
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepLinesTest {

    private val oakLogs = 1521
    private val trees = OptionSource { listOf(StepOption("oak", "Oak tree", OptionIcon.Item(oakLogs))) }
    private val chop = FakeStepType("chop", target = StepTarget("tree", trees), detailsOf = { settings, before ->
        listOf("${settings["amount"] ?: "all"} per lap") + before.stepsGathering(setOf(1)).map { "after step $it" }
    })
    private val gather = FakeStepType("gather", detailsOf = { settings, _ -> listOfNotNull(settings["to"]?.let { "to $it" }) })
    private val lines = StepLines(FlowResolver(StepTypes(listOf(chop, gather))), FakeNames())

    private val oak = StepSettings("chop", mapOf("tree" to "oak", "amount" to "5"))

    @Test
    fun `a step's kind is named with a capital`() {
        assertEquals("Chop label", lines.of(listOf(oak), 0).kind)
    }

    @Test
    fun `a step's icon is its kind's`() {
        assertEquals(StepIcon.Media("chop", 0), lines.of(listOf(oak), 0).icon)
    }

    @Test
    fun `what a step picked leads its lines, then its details`() {
        assertEquals(listOf("Oak tree", "5 per lap"), lines.of(listOf(oak), 0).lines)
    }

    @Test
    fun `nothing picked yet says so`() {
        assertEquals(listOf("not set yet", "all per lap"), lines.of(listOf(StepSettings("chop")), 0).lines)
    }

    @Test
    fun `a kind that picks nothing has its details alone`() {
        assertEquals(listOf("to 1, 2"), lines.of(listOf(StepSettings("gather", mapOf("to" to "1, 2"))), 0).lines)
    }

    @Test
    fun `the details know what the steps before set up`() {
        val flow = listOf(step("gather", "gather"), oak)

        assertEquals(listOf("Oak tree", "5 per lap", "after step 1"), lines.of(flow, 1).lines)
    }

    @Test
    fun `a step reads on one line as its kind, then its lines`() {
        assertEquals("Chop label: Oak tree, 5 per lap", lines.of(listOf(oak), 0).line)
    }

    @Test
    fun `a step with its own spot ends its line there`() {
        val spot = oak.with("spot", "3101 3509")

        assertEquals("Chop label: Oak tree, 5 per lap at 3101, 3509", lines.of(listOf(spot), 0).line)
    }

    @Test
    fun `a step with nothing under its kind reads as its kind`() {
        assertEquals("Gather label", lines.of(listOf(step("gather")), 0).line)
    }

    @Test
    fun `a step of a kind no type knows shows its kind alone`() {
        assertEquals(SlotWords("Dig", icon = null, picked = null, details = emptyList(), goal = null, spot = null), lines.of(listOf(StepSettings("dig")), 0))
    }

    @Test
    fun `a kind's name is capitalised as the builder shows it`() {
        assertEquals("Pick up", capitalised("pick up"))
    }

    private val goaled = FakeStepType("goalchop", skill = Skill.WOODCUTTING, goal = true, detailsOf = { _, _ -> listOf("5 per lap") })
    private val goalLines = StepLines(FlowResolver(StepTypes(listOf(goaled))), FakeNames())
    private val oaksTo45 = StepSettings("goalchop", mapOf("goal" to "level", "goalLevel" to "45"))

    @Test
    fun `a step's goal is its last line`() {
        assertEquals(listOf("5 per lap", "until Woodcutting 45"), goalLines.of(listOf(oaksTo45), 0).lines)
    }

    @Test
    fun `a goal met says so`() {
        assertEquals("Goal met: Woodcutting 45", goalLines.of(listOf(oaksTo45), 0, met = setOf(0)).goal)
    }

    @Test
    fun `a goal that is wrong gives no line, its reason showing instead`() {
        assertNull(goalLines.of(listOf(StepSettings("goalchop", mapOf("goal" to "level"))), 0).goal)
    }

    @Test
    fun `a step without a goal has no goal line`() {
        assertNull(goalLines.of(listOf(StepSettings("goalchop")), 0).goal)
    }

    @Test
    fun `another step's goal met leaves this one's goal to come`() {
        assertEquals("until Woodcutting 45", goalLines.of(listOf(oaksTo45, oaksTo45), 1, met = setOf(0)).goal)
    }

    /** A bank-like kind whose slot lists what the run leaves out, fed by a burn step taking logs. */
    private object Stock : StepType, LeavesOut {
        override val kind = "stock"
        override val label = "stock"
        override val description = "Stocks up."

        override fun fields(names: game.idle.flow.option.GameNames): List<StepField> = emptyList()

        override fun icon(settings: StepSettings): StepIcon = StepIcon.BANK

        override fun details(settings: StepSettings, context: FlowContext): List<String> = details(settings, context, emptySet())

        override fun details(settings: StepSettings, context: FlowContext, leftOut: Set<Int>): List<String> = listOf("left $leftOut")

        override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep =
            if (settings["word"] == "bad") throw FlowError("'bad' is refused") else FakeBankStep(withdraws = setOf(1511))
    }

    private val burn = FakeStepType("burn", resolved = { FakeStep("burn", needs = listOf(StepNeeds(inputs = listOf(1511)))) })
    private val stockLines = StepLines(FlowResolver(StepTypes(listOf(burn, Stock))), FakeNames())

    @Test
    fun `a bank step's slot says what the run's met goals leave out`() {
        assertEquals(listOf("left [1511]"), stockLines.of(listOf(StepSettings("burn"), StepSettings("stock")), 1, met = setOf(0)).details)
    }

    @Test
    fun `a bank step leaves nothing out before any goal is met`() {
        assertEquals(listOf("left []"), stockLines.of(listOf(StepSettings("burn"), StepSettings("stock")), 1).details)
    }

    @Test
    fun `a bank step that cannot work leaves nothing out`() {
        val steps = listOf(StepSettings("burn"), StepSettings("stock", mapOf("word" to "bad")))

        assertEquals(listOf("left []"), stockLines.of(steps, 1, met = setOf(0)).details)
    }

    @Test
    fun `the goal joins the step's one line`() {
        assertEquals("Goalchop label: 5 per lap, until Woodcutting 45", goalLines.of(listOf(oaksTo45), 0).line)
    }
}
