package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BankStepTypeTest {

    private val draynor = Bank("draynor", "Draynor bank", Tile(3091, 3242))
    private val varrock = Bank("varrock_west", "Varrock west bank", Tile(3186, 3440))
    private val upstairs = Bank("gnome", "Gnome bank", Tile(3100, 3250, 1))
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
    fun `the builder offers nearest, then every bank`() {
        assertEquals(listOf("nearest", "varrock_west", "draynor"), (bank.fields[0] as StepField.Choice).choices(settings()))
    }

    @Test
    fun `the nearest bank is the closest in a straight line on the player's floor`() {
        val step = BankStep(listOf(varrock, draynor, upstairs))

        assertEquals(draynor, step.nearest(Tile(3100, 3250)))
        assertEquals(varrock, step.nearest(Tile(3180, 3400)))
        assertEquals(upstairs, step.nearest(Tile(3180, 3400, 1)))
    }

    @Test
    fun `no bank on the player's floor is no nearest bank`() {
        assertNull(BankStep(listOf(draynor)).nearest(Tile(3100, 3250, 2)))
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
}
