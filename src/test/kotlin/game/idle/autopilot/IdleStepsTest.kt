package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IdleStepsTest {

    private val steps = IdleSteps(LocationCatalog.parse(catalogJson(locationJson())))

    @Test
    fun `the builder cycles through chop, drop and bank`() {
        assertEquals(listOf(steps.chop, DropStepType, BankStepType), steps.grammar.types)
    }
}
