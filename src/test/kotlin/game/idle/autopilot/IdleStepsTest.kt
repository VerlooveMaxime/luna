package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.location.BankCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class IdleStepsTest {

    private val types = IdleSteps(BankCatalog(emptyList())).grammar.types

    @Test
    fun `the builder cycles through gathering, processing, then walking, dropping and banking`() {
        assertEquals(listOf(ChopStepType, FishStepType, LightStepType, CookStepType, WalkStepType, DropStepType), types.take(6))
        assertInstanceOf(BankStepType::class.java, types[6])
        assertEquals(7, types.size)
    }
}
