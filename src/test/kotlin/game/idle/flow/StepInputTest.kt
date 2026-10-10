package game.idle.flow

import game.idle.flow.option.InputSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepInputTest {

    private val logs = setOf(1511, 1521)

    @Test
    fun `the steps that gather an item come in flow order, each once`() {
        assertEquals(listOf(1, 3), FlowContext(gatheredBy = mapOf(1521 to 3, 1511 to 1, 317 to 2, 1519 to 3)).stepsGathering(setOf(1511, 1521, 1519)))
    }

    @Test
    fun `items nobody gathers come from no step`() {
        assertEquals(listOf<Int>(), FlowContext(gatheredBy = mapOf(317 to 2)).stepsGathering(logs))
    }

    @Test
    fun `one step gathering the input is named`() {
        assertEquals("from step 2", StepInput.detail("Logs", InputSource.EARLIER_STEPS, FlowContext(gatheredBy = mapOf(1521 to 2)), logs))
    }

    @Test
    fun `several steps gathering it are all named`() {
        assertEquals("from steps 1, 2", StepInput.detail("Logs", InputSource.EARLIER_STEPS, FlowContext(gatheredBy = mapOf(1511 to 1, 1521 to 2)), logs))
    }

    @Test
    fun `no step gathering it says so`() {
        assertEquals("No raw food before it", StepInput.detail("Raw food", InputSource.EARLIER_STEPS, FlowContext(), setOf(317)))
    }

    @Test
    fun `an input from the bank says so whatever the steps before get`() {
        assertEquals("from the bank", StepInput.detail("Logs", InputSource.BANK, FlowContext(gatheredBy = mapOf(1521 to 2)), logs))
    }
}
