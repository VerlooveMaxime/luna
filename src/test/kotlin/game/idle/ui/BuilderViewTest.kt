package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType
import game.idle.flow.StepField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuilderViewTest {

    private val flow = listOf("chop oak @draynor", "bank deposit all", "drop")
    private val chop = FakeStepType("chop", listOf(StepField("tree") { listOf("oak") }, StepField("bush") { emptyList() }))
    private val drop = FakeStepType("drop")
    private val draft = FlowDraft.first(listOf(chop, drop))

    @Test
    fun `rows number the steps and mark the running one`() {
        val texts = BuilderView.stateTexts(IdleState(flow = flow, stepIndex = 1, running = true))

        assertEquals("1. chop oak @draynor", texts[FlowWidgets.rowText(0)])
        assertEquals("@gre@2. bank deposit all", texts[FlowWidgets.rowText(1)])
        assertEquals("3. drop", texts[FlowWidgets.rowText(2)])
    }

    @Test
    fun `a stopped flow marks no row`() {
        val texts = BuilderView.stateTexts(IdleState(flow = flow, stepIndex = 1, running = false))

        assertEquals("2. bank deposit all", texts[FlowWidgets.rowText(1)])
    }

    @Test
    fun `rows with a step get their buttons, empty rows none`() {
        val texts = BuilderView.stateTexts(IdleState(flow = flow))

        assertEquals(listOf("up", "down", "del"), listOf(FlowWidgets.rowUp(2), FlowWidgets.rowDown(2), FlowWidgets.rowDelete(2)).map(texts::getValue))
        assertEquals(listOf("", "", "", ""), listOf(FlowWidgets.rowText(3), FlowWidgets.rowUp(3), FlowWidgets.rowDown(3), FlowWidgets.rowDelete(3)).map(texts::getValue))
    }

    @Test
    fun `the status line follows the state`() {
        assertEquals("No steps yet.", BuilderView.stateTexts(IdleState())[FlowWidgets.STATUS])
        assertEquals("Stopped. 3 steps.", BuilderView.stateTexts(IdleState(flow = flow))[FlowWidgets.STATUS])
        assertEquals("@gre@Running step 2/3", BuilderView.stateTexts(IdleState(flow = flow, stepIndex = 1, running = true))[FlowWidgets.STATUS])
    }

    @Test
    fun `a draft shows its kind, its fields and their labels, blank where it has none`() {
        val texts = BuilderView.draftTexts(draft, editing = null)

        assertEquals("chop label", texts[FlowWidgets.DRAFT_KIND])
        assertEquals(listOf("tree", "bush", ""), FlowWidgets.DRAFT_FIELD_LABELS.map(texts::getValue))
        assertEquals(listOf("oak", "-", ""), FlowWidgets.DRAFT_FIELDS.map(texts::getValue))
    }

    @Test
    fun `a kind of step without fields blanks every field and label`() {
        val texts = BuilderView.draftTexts(draft.nextType(listOf(chop, drop)), editing = null)

        assertEquals(List(6) { "" }, (FlowWidgets.DRAFT_FIELD_LABELS + FlowWidgets.DRAFT_FIELDS).map(texts::getValue))
    }

    @Test
    fun `a new step is labelled and added`() {
        val texts = BuilderView.draftTexts(draft, editing = null)

        assertEquals("New step (click a field to change it)", texts[FlowWidgets.DRAFT_LABEL])
        assertEquals("Add step", texts[FlowWidgets.DRAFT_ADD])
    }

    @Test
    fun `an edited step is labelled with its number and saved`() {
        val texts = BuilderView.draftTexts(draft, editing = 1)

        assertEquals("Editing step 2 (click a field to change it)", texts[FlowWidgets.DRAFT_LABEL])
        assertEquals("Save step 2", texts[FlowWidgets.DRAFT_ADD])
    }

    @Test
    fun `the full view adds the draft and the message to the rows`() {
        val texts = BuilderView.texts(IdleState(flow = flow), draft, "Step 3 added: drop", editing = null)

        assertEquals("1. chop oak @draynor", texts[FlowWidgets.rowText(0)])
        assertEquals("chop label", texts[FlowWidgets.DRAFT_KIND])
        assertEquals("Step 3 added: drop", texts[FlowWidgets.MESSAGE])
    }

    @Test
    fun `the tab shows the three status lines`() {
        val texts = BuilderView.tabTexts(IdleState(flow = flow, stepIndex = 1, running = true))

        assertEquals(mapOf(FlowWidgets.TAB_STATUS_1 to "Autopilot: running", FlowWidgets.TAB_STATUS_2 to "Step 2/3", FlowWidgets.TAB_STATUS_3 to "bank deposit all"), texts)
    }
}
