package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DropStepTypeTest {

    private val logs = 1511
    private val notedLogs = 1512
    private val oak = 1521
    private val bucket = 1925
    private val items = ItemCatalog(mapOf(logs to "Logs", oak to "Oak logs", bucket to "Bucket"), notes = mapOf(notedLogs to logs))
    private val names = FakeNames(items = mapOf(logs to "Logs", notedLogs to "Logs", oak to "Oak logs", bucket to "Bucket"))
    private val type = DropStepType(items, names)
    private val gathered = StepSettings("drop")
    private val chosen = StepSettings("drop", mapOf("drop" to "chosen", "items" to "$logs,$oak"))

    private fun fields() = type.fields(names)

    private fun note(before: FlowContext): String = (fields()[1] as StepField.Note).text(gathered, before)

    @Test
    fun `a drop step drops what was gathered by default`() {
        assertEquals(DropChoice.GATHERED, DropStepType.choice(StepSettings("drop", mapOf("drop" to "some"))))
    }

    @Test
    fun `the configure screen has the drop toggle, the note on gathered and the list on chosen`() {
        assertEquals(
            listOf(
                "Drop (left): toggle drop gathered 'Gathered' / chosen 'Chosen'",
                "Drops (left): note",
                "Chosen (left): list items on 5 rows, 'What would you like to drop?'",
            ),
            described(fields()),
        )
    }

    @Test
    fun `the toggle lights the choice kept`() {
        assertEquals("chosen", (fields()[0] as StepField.Toggle).current(chosen, FlowContext()))
    }

    @Test
    fun `the note shows on gathered, the list on chosen`() {
        assertEquals(listOf(true, false, false, true), listOf(fields()[1].visible(gathered), fields()[1].visible(chosen), fields()[2].visible(gathered), fields()[2].visible(chosen)))
    }

    @Test
    fun `the note names what the flow gathers`() {
        assertEquals("Logs, oak logs", note(FlowContext(lap = setOf(logs, oak))))
    }

    @Test
    fun `the note names items that share a name once`() {
        assertEquals("Logs, oak logs", note(FlowContext(lap = setOf(logs, notedLogs, oak))))
    }

    @Test
    fun `the note says when no step gathers anything`() {
        assertEquals("Nothing any step gathers yet", note(FlowContext()))
    }

    @Test
    fun `the chosen list offers what the bag holds before any other item`() {
        val options = (fields()[2] as StepField.Items).source.options(OptionContext(OptionFacts(bag = setOf(bucket))))

        assertEquals("Bucket in your bag", options.first().let { "${it.label} ${it.note}" })
    }

    @Test
    fun `drop on gathered drops what the whole flow gathers`() {
        assertEquals(DropStep(setOf(logs, oak)), type.resolve(gathered, FlowContext(gathered = setOf(logs), lap = setOf(logs, oak))))
    }

    @Test
    fun `drop on gathered when no step gathers anything cannot work`() {
        val error = assertThrows<FlowError> { type.resolve(gathered, FlowContext(gathered = setOf(logs))) }

        assertEquals("Needs a step that gathers something", error.message)
    }

    @Test
    fun `drop on chosen drops every item of the names picked, notes too`() {
        assertEquals(DropStep(setOf(logs, notedLogs, oak)), type.resolve(chosen, FlowContext()))
    }

    @Test
    fun `ids that name no item are left out of chosen`() {
        assertEquals(DropStep(setOf(oak)), type.resolve(StepSettings("drop", mapOf("drop" to "chosen", "items" to "99999,$oak")), FlowContext()))
    }

    @Test
    fun `drop on chosen with nothing picked cannot work`() {
        val error = assertThrows<FlowError> { type.resolve(StepSettings("drop", mapOf("drop" to "chosen")), FlowContext()) }

        assertEquals("Needs chosen items", error.message)
    }

    @Test
    fun `the steps after a drop step know what the steps before it knew`() {
        val context = FlowContext(gathered = setOf(logs))

        assertEquals(context, DropStep(setOf(logs)).after(context))
    }

    @Test
    fun `a drop step shows the inventory tab's backpack`() {
        assertEquals(StepIcon.Media("sideicons", 3), type.icon(gathered))
    }

    @Test
    fun `a drop step says what it does`() {
        assertEquals("Drops what the flow gathers or the items picked, where it stands.", type.description)
    }

    @Test
    fun `a drop step's slot on gathered says it drops gathered items`() {
        assertEquals(listOf("gathered items"), type.details(gathered, FlowContext()))
    }

    @Test
    fun `a drop step's slot on chosen has no line under its items`() {
        assertEquals(emptyList<String>(), type.details(chosen, FlowContext()))
    }

    @Test
    fun `a drop step on chosen shows its items with the first one's picture`() {
        assertEquals(StepPick("Logs, oak logs", OptionIcon.Item(logs)), type.pick(chosen, names))
    }

    @Test
    fun `a drop step on gathered picks nothing`() {
        assertNull(type.pick(gathered, names))
    }

    @Test
    fun `a drop step has no target`() {
        assertNull(type.target(names))
    }

    @Test
    fun `a new drop step starts on gathered, shows no level and searches with the bank's counts`() {
        assertEquals(
            listOf(StepSettings("drop"), null, InputSource.BANK),
            listOf(type.newSettings(FlowContext()), type.skill(gathered), type.input(gathered, FlowContext())),
        )
    }

    @Test
    fun `a drop step needs nothing in the bag`() {
        assertEquals(emptyList<StepNeeds>(), DropStep(setOf(logs)).needs())
    }

    @Test
    fun `a drop step empties the bag of what it drops`() {
        assertEquals(listOf(true, false), listOf(DropStep(setOf(logs)).empties(logs), DropStep(setOf(logs)).empties(oak)))
    }
}
