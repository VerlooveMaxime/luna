package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuilderViewTest {

    private val flow = listOf("chop oak @draynor", "bank deposit all", "drop").map(::line)
    private val chop = FakeStepType(
        "chop",
        listOf(StepField.Choice("tree", "tree") { listOf("oak") }, StepField.Choice("bush", "bush") { emptyList() }),
    )
    private val drop = FakeStepType("drop")
    private val draft = FlowDraft.first(listOf(chop, drop))
    private val view = BuilderView { it["text"] ?: it.kind }

    private fun line(text: String) = StepSettings("line", mapOf("text" to text))

    @Test
    fun `rows number the steps and mark the running one`() {
        val texts = view.stateTexts(IdleState(steps = flow, stepIndex = 1, running = true))

        assertEquals("1. chop oak @draynor", texts[FlowWidgets.rowText(0)])
        assertEquals("@gre@2. bank deposit all", texts[FlowWidgets.rowText(1)])
        assertEquals("3. drop", texts[FlowWidgets.rowText(2)])
    }

    @Test
    fun `a stopped flow marks no row`() {
        val texts = view.stateTexts(IdleState(steps = flow, stepIndex = 1, running = false))

        assertEquals("2. bank deposit all", texts[FlowWidgets.rowText(1)])
    }

    @Test
    fun `rows with a step get their buttons, empty rows none`() {
        val texts = view.stateTexts(IdleState(steps = flow))

        assertEquals(listOf("up", "down", "del"), listOf(FlowWidgets.rowUp(2), FlowWidgets.rowDown(2), FlowWidgets.rowDelete(2)).map(texts::getValue))
        assertEquals(listOf("", "", "", ""), listOf(FlowWidgets.rowText(3), FlowWidgets.rowUp(3), FlowWidgets.rowDown(3), FlowWidgets.rowDelete(3)).map(texts::getValue))
    }

    @Test
    fun `the status line follows the state`() {
        assertEquals("No steps yet.", view.stateTexts(IdleState())[FlowWidgets.STATUS])
        assertEquals("Stopped. 3 steps.", view.stateTexts(IdleState(steps = flow))[FlowWidgets.STATUS])
        assertEquals("@gre@Running step 2/3", view.stateTexts(IdleState(steps = flow, stepIndex = 1, running = true))[FlowWidgets.STATUS])
    }

    @Test
    fun `a draft shows its kind, its fields and their labels, blank where it has none`() {
        val texts = view.draftTexts(draft, editing = null)

        assertEquals("chop label", texts[FlowWidgets.DRAFT_KIND])
        assertEquals(listOf("tree", "bush", "", ""), FlowWidgets.DRAFT_FIELD_LABELS.map(texts::getValue))
        assertEquals(listOf("oak", "-", "", ""), FlowWidgets.DRAFT_FIELDS.map(texts::getValue))
    }

    @Test
    fun `a kind of step without fields blanks every field and label`() {
        val texts = view.draftTexts(draft.nextType(listOf(chop, drop)), editing = null)

        assertEquals(List(8) { "" }, (FlowWidgets.DRAFT_FIELD_LABELS + FlowWidgets.DRAFT_FIELDS).map(texts::getValue))
    }

    @Test
    fun `a new step is labelled and added`() {
        val texts = view.draftTexts(draft, editing = null)

        assertEquals("New step (click a field to change it)", texts[FlowWidgets.DRAFT_LABEL])
        assertEquals("Add step", texts[FlowWidgets.DRAFT_ADD])
    }

    @Test
    fun `an edited step is labelled with its number and saved`() {
        val texts = view.draftTexts(draft, editing = 1)

        assertEquals("Editing step 2 (click a field to change it)", texts[FlowWidgets.DRAFT_LABEL])
        assertEquals("Save step 2", texts[FlowWidgets.DRAFT_ADD])
    }

    @Test
    fun `the full view adds the draft and the message to the rows`() {
        val texts = view.texts(IdleState(steps = flow), draft, "Step 3 added: drop", editing = null)

        assertEquals("1. chop oak @draynor", texts[FlowWidgets.rowText(0)])
        assertEquals("chop label", texts[FlowWidgets.DRAFT_KIND])
        assertEquals("Step 3 added: drop", texts[FlowWidgets.MESSAGE])
    }

    @Test
    fun `the tab shows the three status lines`() {
        val texts = view.tabTexts(IdleState(steps = flow, stepIndex = 1, running = true))

        assertEquals(mapOf(FlowWidgets.TAB_STATUS_1 to "Autopilot: running", FlowWidgets.TAB_STATUS_2 to "Step 2/3", FlowWidgets.TAB_STATUS_3 to "bank deposit all"), texts)
    }

    @Test
    fun `a choice shows its value as its field words it`() {
        val amount = FakeStepType("light", listOf(StepField.Choice("amount", "amount", display = { it.ifEmpty { "all" } }) { listOf("", "5") }))

        assertEquals("all", view.draftTexts(FlowDraft.first(listOf(amount)), editing = null)[FlowWidgets.DRAFT_FIELDS[0]])
    }

    @Test
    fun `a tile field shows its tile, or a dash before one is picked`() {
        val walk = FakeStepType("walk", listOf(StepField.MapTile("tile", "to")))
        val draft = FlowDraft.first(listOf(walk))

        assertEquals("-", view.draftTexts(draft, editing = null)[FlowWidgets.DRAFT_FIELDS[0]])
        assertEquals("3200 3201", view.draftTexts(draft.withBlankTiles("3200 3201"), editing = null)[FlowWidgets.DRAFT_FIELDS[0]])
    }
}
