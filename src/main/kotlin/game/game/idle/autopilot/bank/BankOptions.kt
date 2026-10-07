package game.idle.autopilot.bank

import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.location.BankCatalog

/** The banks a bank step can use: the nearest one when the step starts (kept on top), or one of the catalog's. */
class BankOptions(private val catalog: BankCatalog) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        listOf(NEAREST) + catalog.banks.map { StepOption(it.id, it.name, OptionIcon.Bank) }

    private companion object {
        val NEAREST = StepOption(
            BankStepType.NEAREST, "Nearest bank", OptionIcon.Bank, note = "the closest when the step starts", group = -1,
        )
    }
}
