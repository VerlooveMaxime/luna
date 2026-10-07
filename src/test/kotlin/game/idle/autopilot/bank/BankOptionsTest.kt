package game.idle.autopilot.bank

import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.idle.location.Bank
import game.idle.location.BankCatalog
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BankOptionsTest {

    private val draynor = Bank("draynor", "Draynor Village bank", Tile(3091, 3242))

    @Test
    fun `the nearest bank comes first, then every bank of the catalog`() {
        val rows = BankOptions(BankCatalog(listOf(draynor))).options(OptionContext())

        assertEquals(
            listOf(
                StepOption("nearest", "Nearest bank", OptionIcon.Bank, "the closest when the step starts", group = -1),
                StepOption("draynor", "Draynor Village bank", OptionIcon.Bank),
            ),
            rows,
        )
    }
}
