package game.idle.autopilot

import game.idle.autopilot.bank.BankStepType
import game.idle.autopilot.cooking.CookStepType
import game.idle.autopilot.drop.DropStepType
import game.idle.autopilot.fighting.FightStepType
import game.idle.autopilot.fighting.FightTargetCatalog
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
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class IdleStepsTest {

    private val types = IdleSteps(BankCatalog(emptyList()), RecipeCatalog(emptyList()), FightTargetCatalog(emptyList())).types.all

    @Test
    fun `the builder cycles through gathering, processing, fighting, then walking, dropping and banking`() {
        assertEquals(listOf(ChopStepType, MineStepType, FishStepType, LightStepType, CookStepType), types.take(5))
        assertInstanceOf(MakeStepType::class.java, types[5])
        assertEquals(listOf(SmeltStepType, SmithStepType), types.subList(6, 8))
        assertInstanceOf(FightStepType::class.java, types[8])
        assertEquals(listOf(WalkStepType, DropStepType), types.subList(9, 11))
        assertInstanceOf(BankStepType::class.java, types[11])
        assertEquals(12, types.size)
    }

    /** Luna's make tables name their items from the cache, so loading needs the test world's definitions. */
    @Test
    fun `the steps load over the data files`() {
        TestWorld.context

        assertEquals(types.map { it.kind }, IdleSteps.load().types.all.map { it.kind })
    }
}
