package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BankStepTypeTest {

    private val draynor = Bank("draynor", "Draynor bank", Tile(3091, 3242))
    private val varrock = Bank("varrock_west", "Varrock west bank", Tile(3186, 3440))
    private val bank = BankStepType(BankCatalog(listOf(varrock, draynor)))

    private fun settings(bankId: String? = null) = StepSettings("bank", bankId?.let { mapOf("bank" to it) } ?: emptyMap())

    @Test
    fun `a bank step reads as its bank, nearest when it names none`() {
        assertEquals("bank draynor", bank.summary(settings("draynor")))
        assertEquals("bank nearest", bank.summary(settings()))
    }

    @Test
    fun `bank nearest picks among every bank`() {
        assertEquals(BankStep(listOf(varrock, draynor)), bank.resolve(settings("nearest"), FlowContext()))
    }

    @Test
    fun `a bank step that names no bank goes to the nearest`() {
        assertEquals(BankStep(listOf(varrock, draynor)), bank.resolve(settings(), FlowContext()))
    }

    @Test
    fun `a named bank is the only one, whatever the case`() {
        assertEquals(BankStep(listOf(draynor)), bank.resolve(settings("Draynor"), FlowContext()))
    }

    @Test
    fun `an unknown bank lists the known ones`() {
        assertRejected("Unknown bank 'lumbridge'. Banks: draynor, varrock_west") { bank.resolve(settings("lumbridge"), FlowContext()) }
    }

    @Test
    fun `the configure screen searches the bank and notes what it deposits`() {
        val fields = bank.fields(FakeNames())

        assertEquals(listOf("Bank (left): search bank, 'Which bank?'", "Deposit (left): note"), described(fields))
        assertEquals("Everything but tools", (fields[1] as StepField.Note).text(settings(), FlowContext()))
    }

    @Test
    fun `the steps after a bank step know what the steps before it knew`() {
        val context = FlowContext(gathered = setOf(1511))

        assertEquals(context, BankStep(listOf(draynor)).after(context))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<FlowError> { action() }

        assertEquals(message, error.message)
    }

    @Test
    fun `a bank step shows the minimap's bank icon`() {
        assertEquals(StepIcon.Media("mapfunction", 5), bank.icon(settings()))
    }

    @Test
    fun `a bank step's target is its bank among the banks`() {
        val target = bank.target(FakeNames())

        assertEquals(listOf("bank", "Draynor bank"), listOf(target.key, target.picked(settings("draynor"))?.label))
    }

    @Test
    fun `a bank step without a bank picks the nearest`() {
        assertEquals("Nearest bank", bank.target(FakeNames()).picked(settings())?.label)
    }

    @Test
    fun `a bank step's slot says it keeps tools`() {
        assertEquals(listOf("keeps tools"), bank.details(settings(), FlowContext()))
    }
}
