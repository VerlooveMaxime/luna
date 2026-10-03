package game.idle.ui

import game.idle.flow.FakeStepType
import game.idle.flow.FlowStep
import game.idle.flow.StepField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowDraftTest {

    // The item field comes first but depends on the kind field after it, so settling takes two rounds.
    private val pick = FakeStepType(
        "pick",
        listOf(
            StepField.Choice("item") { values ->
                when (values[1]) {
                    "fruit" -> listOf("apple", "tomato")
                    "veg" -> listOf("leek", "tomato")
                    else -> emptyList()
                }
            },
            StepField.Choice("kind") { listOf("fruit", "veg") },
        ),
    )
    private val rest = FakeStepType("rest")
    private val wait = FakeStepType("wait", listOf(StepField.Choice("time") { emptyList() }))
    private val walk = FakeStepType("walk", listOf(StepField.MapTile("to"), StepField.Choice("pace") { listOf("slow", "fast") }))
    private val run = FakeStepType(
        "run",
        listOf(
            StepField.Choice("pace", default = "fast") { listOf("slow", "fast") },
            StepField.Choice("shoes", default = "boots") { listOf("bare", "sandals") },
        ),
    )
    private val types = listOf(pick, rest)
    private val draft = FlowDraft.first(types)

    @Test
    fun `the first draft is the first kind with each field at its first choice`() {
        assertEquals(pick, draft.type)
        assertEquals(listOf("apple", "fruit"), draft.values)
    }

    @Test
    fun `the line is what the kind of step writes for the values`() {
        assertEquals("pick apple fruit", draft.line())
    }

    @Test
    fun `a field cycles through its choices and wraps`() {
        assertEquals("tomato", draft.nextValue(0).values[0])
        assertEquals("apple", draft.nextValue(0).nextValue(0).values[0])
    }

    @Test
    fun `a field no longer offering its value moves to its first choice`() {
        assertEquals(listOf("leek", "veg"), draft.nextValue(1).values)
    }

    @Test
    fun `a field still offering its value keeps it`() {
        assertEquals(listOf("tomato", "veg"), draft.nextValue(0).nextValue(1).values)
    }

    @Test
    fun `a field without choices stays blank`() {
        val waiting = FlowDraft.first(listOf(wait))

        assertEquals(listOf(""), waiting.nextValue(0).values)
    }

    @Test
    fun `a field the kind of step does not have changes nothing`() {
        val resting = draft.nextType(types)

        assertEquals(resting, resting.nextValue(0))
    }

    @Test
    fun `kinds cycle and wrap`() {
        assertEquals(rest, draft.nextType(types).type)
        assertEquals(pick, draft.nextType(types).nextType(types).type)
    }

    @Test
    fun `values are kept per kind while cycling`() {
        val back = draft.nextValue(0).nextType(types).nextType(types)

        assertEquals(listOf("tomato", "fruit"), back.values)
    }

    @Test
    fun `editing loads the step's kind and values as they are`() {
        val editing = draft.editing(FlowStep(pick, listOf("rock", "veg")))

        assertEquals(pick, editing.type)
        assertEquals(listOf("rock", "veg"), editing.values)
    }

    @Test
    fun `editing another kind keeps what this kind had`() {
        val back = draft.nextValue(0).editing(FlowStep(rest, emptyList())).nextType(types)

        assertEquals(listOf("tomato", "fruit"), back.values)
    }

    @Test
    fun `a field starts at its default when it is offered, else at its first choice`() {
        assertEquals(listOf("fast", "bare"), FlowDraft.first(listOf(run)).values)
    }

    @Test
    fun `a tile field starts blank and is not settled away`() {
        val walking = FlowDraft.first(listOf(walk))

        assertEquals(listOf("", "slow"), walking.values)
        assertEquals(listOf("", "fast"), walking.nextValue(1).values)
    }

    @Test
    fun `a tile field does not cycle`() {
        val walking = FlowDraft.first(listOf(walk))

        assertEquals(walking, walking.nextValue(0))
    }

    @Test
    fun `a value can be set directly`() {
        assertEquals(listOf("3086 3233", "slow"), FlowDraft.first(listOf(walk)).withValue(0, "3086 3233").values)
    }

    @Test
    fun `blank tiles are filled in, set ones and other fields are kept`() {
        val walking = FlowDraft.first(listOf(walk))

        assertEquals(listOf("1 2", "slow"), walking.withBlankTiles("1 2").values)
        assertEquals(listOf("3 4", "slow"), walking.withValue(0, "3 4").withBlankTiles("1 2").values)
        assertEquals(listOf(""), FlowDraft.first(listOf(wait)).withBlankTiles("1 2").values)
    }
}
