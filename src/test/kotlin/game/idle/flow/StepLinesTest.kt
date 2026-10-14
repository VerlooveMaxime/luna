package game.idle.flow

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
        assertEquals(SlotWords("Dig", icon = null, picked = null, details = emptyList(), spot = null), lines.of(listOf(StepSettings("dig")), 0))
    }

    @Test
    fun `a kind's name is capitalised as the builder shows it`() {
        assertEquals("Pick up", capitalised("pick up"))
    }
}
