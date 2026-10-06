package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishStepType
import game.idle.autopilot.making.MakeStepType
import game.idle.autopilot.making.RecipeCatalog
import game.idle.autopilot.mining.MineStepType
import game.idle.autopilot.smelting.SmeltStepType
import game.idle.autopilot.smithing.SmithStepType
import game.idle.autopilot.walk.WalkStepType
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.location.BankCatalog
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class IdleStepsTest {

    private val types = IdleSteps(BankCatalog(emptyList()), RecipeCatalog(emptyList())).grammar.types

    @Test
    fun `the builder cycles through gathering, processing, then walking, dropping and banking`() {
        assertEquals(listOf(ChopStepType, MineStepType, FishStepType, LightStepType, CookStepType), types.take(5))
        assertInstanceOf(MakeStepType::class.java, types[5])
        assertEquals(listOf(SmeltStepType, SmithStepType, WalkStepType, DropStepType), types.subList(6, 10))
        assertInstanceOf(BankStepType::class.java, types[10])
        assertEquals(11, types.size)
    }
}
