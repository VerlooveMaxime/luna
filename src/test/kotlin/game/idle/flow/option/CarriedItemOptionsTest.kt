package game.idle.flow.option

import game.idle.flow.FlowContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CarriedItemOptionsTest {

    private val bucket = 1925
    private val logs = 1511
    private val notedLogs = 1512
    private val bones = 526
    private val catalog = ItemCatalog(mapOf(bucket to "Bucket", logs to "Logs", bones to "Bones"), notes = mapOf(notedLogs to logs))
    private val options = CarriedItemOptions(catalog, FakeNames(items = mapOf(bucket to "Bucket", logs to "Logs", bones to "Bones")))

    private fun rows(lap: Set<Int> = emptySet(), bag: Set<Int> = emptySet()): List<StepOption> =
        options.options(OptionContext(OptionFacts(bag = bag), before = FlowContext(lap = lap)))

    private fun row(id: Int, name: String, note: String, group: Int) = StepOption(id.toString(), name, OptionIcon.Item(id), note, group = group)

    @Test
    fun `what the flow gathers comes first`() {
        assertEquals(row(logs, "Logs", "gathered", group = -2), rows(lap = setOf(logs)).first())
    }

    @Test
    fun `what the bag holds comes next`() {
        assertEquals(
            listOf(row(logs, "Logs", "gathered", group = -2), row(bucket, "Bucket", "in your bag", group = -1)),
            rows(lap = setOf(logs), bag = setOf(bucket)).take(2),
        )
    }

    @Test
    fun `a noted item in the bag shows as its item`() {
        assertEquals(row(logs, "Logs", "in your bag", group = -1), rows(bag = setOf(notedLogs)).first())
    }

    @Test
    fun `an item gathered and carried shows once, as gathered`() {
        assertEquals(listOf("gathered", "any item", "any item"), rows(lap = setOf(logs), bag = setOf(logs)).map { it.note })
    }

    @Test
    fun `every other item follows once`() {
        assertEquals(
            listOf(row(bucket, "Bucket", "any item", group = 0), row(bones, "Bones", "any item", group = 0)),
            rows(lap = setOf(logs)).drop(1),
        )
    }
}
