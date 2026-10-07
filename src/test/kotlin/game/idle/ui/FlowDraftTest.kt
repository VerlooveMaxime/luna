package game.idle.ui

import game.idle.flow.FakeStepType
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowDraftTest {

    // The item field comes first but depends on the kind field after it, so settling takes two rounds.
    private val pick = FakeStepType(
        "pick",
        listOf(
            StepField.Choice("item", "item") { settings ->
                when (settings["kind"]) {
                    "fruit" -> listOf("apple", "tomato")
                    "veg" -> listOf("leek", "tomato")
                    else -> emptyList()
                }
            },
            StepField.Choice("kind", "kind") { listOf("fruit", "veg") },
        ),
    )
    private val rest = FakeStepType("rest")
    private val wait = FakeStepType("wait", listOf(StepField.Choice("time", "time") { emptyList() }))
    private val walk = FakeStepType("walk", listOf(StepField.MapTile("to", "to"), StepField.Choice("pace", "pace") { listOf("slow", "fast") }))
    private val run = FakeStepType(
        "run",
        listOf(
            StepField.Choice("pace", "pace", default = "fast") { listOf("slow", "fast") },
            StepField.Choice("shoes", "shoes", default = "boots") { listOf("bare", "sandals") },
        ),
    )
    private val types = listOf(pick, rest)
    private val draft = FlowDraft.first(types)

    /** The draft's field values in the builder's order, "" for none. */
    private val FlowDraft.values: List<String>
        get() = type.fields.indices.map(::value)

    @Test
    fun `the first draft is the first kind with each field at its first choice`() {
        assertEquals(pick, draft.type)
        assertEquals(listOf("apple", "fruit"), draft.values)
    }

    @Test
    fun `the settings are the kind's with each field's value under its key`() {
        assertEquals(StepSettings("pick", mapOf("item" to "apple", "kind" to "fruit")), draft.settings)
    }

    @Test
    fun `a field the kind of step does not have has no value`() {
        assertEquals("", draft.value(2))
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
        val editing = draft.editing(pick, StepSettings("pick", mapOf("item" to "rock", "kind" to "veg")))

        assertEquals(pick, editing.type)
        assertEquals(listOf("rock", "veg"), editing.values)
    }

    @Test
    fun `editing another kind keeps what this kind had`() {
        val back = draft.nextValue(0).editing(rest, StepSettings("rest")).nextType(types)

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
