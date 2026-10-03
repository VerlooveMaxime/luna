package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.location.Tile
import game.idle.location.parseOne
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BankStepTypeTest {

    private val varrock = parseOne("bank" to mapOf("x" to 3186, "y" to 3440))

    @Test
    fun `bank deposit all has no values and reads back as itself`() {
        assertEquals(emptyList<String>(), BankStepType.parse(listOf("deposit", "all")))
        assertEquals("bank deposit all", BankStepType.line(emptyList()))
    }

    @Test
    fun `bank only deposits all`() {
        val error = assertThrows<FlowError> { BankStepType.parse(listOf("withdraw", "axe")) }

        assertEquals("The only bank step is 'bank deposit all'", error.message)
    }

    @Test
    fun `bank uses the bank of the location the flow is at`() {
        assertEquals(BankStep(varrock, Tile(3186, 3440)), BankStepType.resolve(emptyList(), FlowContext(location = varrock)))
    }

    @Test
    fun `bank before any location is rejected`() {
        val error = assertThrows<FlowError> { BankStepType.resolve(emptyList(), FlowContext()) }

        assertEquals("bank comes after a chop step, so the flow knows which bank to use", error.message)
    }

    @Test
    fun `bank at a location without a bank is rejected`() {
        val error = assertThrows<FlowError> { BankStepType.resolve(emptyList(), FlowContext(location = parseOne("name" to "Lumbridge"))) }

        assertEquals("Lumbridge has no bank", error.message)
    }
}
