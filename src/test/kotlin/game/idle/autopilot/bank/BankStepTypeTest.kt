package game.idle.autopilot.bank

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepItem
import game.idle.flow.StepNeeds
import game.idle.flow.StepSettings
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BankStepTypeTest {

    private val logs = 1511
    private val oak = 1521
    private val tinderbox = 590

    private val draynor = Bank("draynor", "Draynor bank", Tile(3091, 3242))
    private val varrock = Bank("varrock_west", "Varrock west bank", Tile(3186, 3440))
    private val names = FakeNames(mapOf(logs to "Logs", oak to "Oak logs", tinderbox to "Tinderbox"))
    private val bank = BankStepType(BankCatalog(listOf(varrock, draynor)), ItemCatalog(mapOf(logs to "Logs", oak to "Oak logs")), names)

    private val gathered = BankOrder(DepositRule.Only(emptySet()), emptyList(), IfStuck.SKIP)

    private fun settings(vararg values: Pair<String, String>) = StepSettings("bank", values.toMap())

    private fun fields() = bank.fields(names)

    private fun note(settings: StepSettings, before: FlowContext = FlowContext()) =
        (fields()[4] as StepField.Note).text(settings, before)

    @Test
    fun `a bank step reads as its bank, what it deposits and withdraws`() {
        assertEquals(
            "bank draynor, deposit chosen, withdraw 1 tinderbox, all oak logs",
            bank.summary(settings("bank" to "draynor", "deposit" to "chosen", "withdraw" to "590:1,1521")),
        )
    }

    @Test
    fun `a bank step without settings reads as the nearest bank depositing what was gathered`() {
        assertEquals("bank nearest, deposit gathered", bank.summary(settings()))
    }

    @Test
    fun `bank nearest picks among every bank`() {
        assertEquals(BankStep(listOf(varrock, draynor), gathered), bank.resolve(settings("bank" to "nearest"), FlowContext()))
    }

    @Test
    fun `a bank step that names no bank goes to the nearest`() {
        assertEquals(BankStep(listOf(varrock, draynor), gathered), bank.resolve(settings(), FlowContext()))
    }

    @Test
    fun `a named bank is the only one, whatever the case`() {
        assertEquals(BankStep(listOf(draynor), gathered), bank.resolve(settings("bank" to "Draynor"), FlowContext()))
    }

    @Test
    fun `an unknown bank lists the known ones`() {
        val error = assertThrows<FlowError> { bank.resolve(settings("bank" to "lumbridge"), FlowContext()) }

        assertEquals("Unknown bank 'lumbridge'. Banks: draynor, varrock_west", error.message)
    }

    @Test
    fun `gathered deposits what every step of the flow gathers or makes`() {
        val step = bank.resolve(settings(), FlowContext(gathered = setOf(logs), lap = setOf(logs, oak))) as BankStep

        assertEquals(DepositRule.Only(setOf(logs, oak)), step.order.deposit)
    }

    @Test
    fun `everything deposits the whole bag`() {
        val step = bank.resolve(settings("deposit" to "everything"), FlowContext()) as BankStep

        assertEquals(DepositRule.Everything, step.order.deposit)
    }

    @Test
    fun `chosen deposits the items picked`() {
        val step = bank.resolve(settings("deposit" to "chosen", "depositItems" to "1511,590"), FlowContext()) as BankStep

        assertEquals(DepositRule.Only(setOf(logs, tinderbox)), step.order.deposit)
    }

    @Test
    fun `a deposit choice that is no choice reads as gathered`() {
        assertEquals(DepositChoice.GATHERED, BankStepType.deposit(settings("deposit" to "some")))
    }

    @Test
    fun `withdrawals keep their order and amounts, none meaning as many as fit`() {
        val step = bank.resolve(settings("withdraw" to "590:1,1521"), FlowContext()) as BankStep

        assertEquals(listOf(StepItem(tinderbox, 1), StepItem(oak)), step.order.withdrawals)
    }

    @Test
    fun `a stuck bank step skips itself by default`() {
        assertEquals(IfStuck.SKIP, (bank.resolve(settings(), FlowContext()) as BankStep).order.ifStuck)
    }

    @Test
    fun `a stuck bank step can stop the flow`() {
        assertEquals(IfStuck.STOP, (bank.resolve(settings("stuck" to "stop"), FlowContext()) as BankStep).order.ifStuck)
    }

    @Test
    fun `the configure screen has the bank and withdrawals on the left, deposit and stuck on the right`() {
        assertEquals(
            listOf(
                "Bank (left): search bank, 'Which bank?'",
                "Withdraw (left): list withdraw on 5 rows, 'What would you like to withdraw?'",
                "Deposit (right): toggle deposit everything 'Everything' / gathered 'Gathered' / chosen 'Chosen'",
                "If stuck (right): toggle stuck skip 'Skip this step' / stop 'Stop the flow'",
                "Banks (right): note",
                "Chosen (right): list depositItems on 4 rows, 'What would you like to deposit?'",
            ),
            described(fields()),
        )
    }

    @Test
    fun `withdrawals have amounts, chosen deposits do not`() {
        assertEquals(listOf(true, false), listOf((fields()[1] as StepField.Items).amounts, (fields()[5] as StepField.Items).amounts))
    }

    @Test
    fun `withdrawals and chosen deposits offer any item with the bank's counts`() {
        val options = (fields()[1] as StepField.Items).source.options(OptionContext(facts = OptionFacts(bank = mapOf(oak to 312))))

        assertEquals("312 in bank", options.single { it.value == "$oak" }.note)
    }

    @Test
    fun `the toggles light the choices kept, gathered and skip by default`() {
        val deposit = fields()[2] as StepField.Toggle
        val stuck = fields()[3] as StepField.Toggle

        assertEquals(listOf("gathered", "skip"), listOf(deposit.current(settings(), FlowContext()), stuck.current(settings(), FlowContext())))
    }

    @Test
    fun `the deposit's note shows unless the deposit is chosen, the list only then`() {
        val chosen = settings("deposit" to "chosen")

        assertEquals(listOf(true, false, false, true), listOf(fields()[4].visible(settings()), fields()[4].visible(chosen), fields()[5].visible(settings()), fields()[5].visible(chosen)))
    }

    @Test
    fun `the deposit's note names what the flow gathers`() {
        assertEquals("Logs, oak logs", note(settings(), FlowContext(lap = setOf(logs, oak))))
    }

    @Test
    fun `the deposit's note says when no step gathers anything`() {
        assertEquals("Nothing any step gathers yet", note(settings()))
    }

    @Test
    fun `the deposit's note says everything takes tools too`() {
        assertEquals("All the bag holds, tools too", note(settings("deposit" to "everything")))
    }

    @Test
    fun `a bank step's slot says what it deposits and withdraws`() {
        assertEquals(listOf("Deposit: gathered", "Withdraw: tinderbox, oak logs"), bank.details(settings("withdraw" to "590:1,1521"), FlowContext()))
    }

    @Test
    fun `a bank step's slot counts the items it deposits by choice and says nothing of withdrawals it has none of`() {
        assertEquals(listOf("Deposit: 2 chosen"), bank.details(settings("deposit" to "chosen", "depositItems" to "1511,1521"), FlowContext()))
    }

    @Test
    fun `a bank step says what it does`() {
        assertEquals("Deposits and withdraws at a bank.", bank.description)
    }

    @Test
    fun `a bank step shows the minimap's bank icon`() {
        assertEquals(StepIcon.Media("mapfunction", 5), bank.icon(settings()))
    }

    @Test
    fun `a bank step's target is its bank among the banks`() {
        val target = bank.target(FakeNames())

        assertEquals(listOf("bank", "Draynor bank"), listOf(target.key, target.picked(settings("bank" to "draynor"))?.label))
    }

    @Test
    fun `a bank step without a bank picks the nearest`() {
        assertEquals("Nearest bank", bank.target(FakeNames()).picked(settings())?.label)
    }

    @Test
    fun `the steps after a bank step know what the steps before it knew`() {
        val context = FlowContext(gathered = setOf(logs))

        assertEquals(context, BankStep(listOf(draynor), gathered).after(context))
    }

    @Test
    fun `a bank step needs nothing in the bag`() {
        assertEquals(emptyList<StepNeeds>(), BankStep(listOf(draynor), gathered).needs())
    }

    @Test
    fun `a resolved bank step says what it withdraws`() {
        assertEquals(setOf(tinderbox, oak), BankStep(listOf(draynor), gathered.copy(withdrawals = listOf(StepItem(tinderbox, 1), StepItem(oak)))).withdraws)
    }

    @Test
    fun `a resolved bank step banks what its deposit takes`() {
        val step = BankStep(listOf(draynor), gathered.copy(deposit = DepositRule.Only(setOf(logs))))

        assertEquals(listOf(true, false), listOf(step.banks(logs), step.banks(oak)))
    }

    @Test
    fun `everything takes any item`() {
        assertTrue(DepositRule.Everything.takes(oak))
    }

    @Test
    fun `an empty deposit takes nothing`() {
        assertFalse(DepositRule.Only(emptySet()).takes(oak))
    }
}
