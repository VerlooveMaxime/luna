package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.location.BankCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class IdleStepsTest {

    private val types = IdleSteps(BankCatalog(emptyList())).grammar.types

    @Test
    fun `the builder cycles through chop, walk, drop and bank`() {
        assertEquals(listOf(ChopStepType, WalkStepType, DropStepType), types.take(3))
        assertInstanceOf(BankStepType::class.java, types[3])
        assertEquals(4, types.size)
    }
}
