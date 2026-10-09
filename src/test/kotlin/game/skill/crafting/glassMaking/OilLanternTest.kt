package game.skill.crafting.glassMaking

import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OilLanternTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun name(id: Int): String = ItemDefinition.ALL.retrieve(id).name

    @Test
    fun `an oil lamp on an oil lantern frame makes an oil lantern`() {
        assertEquals(listOf("Oil lamp", "Oil lantern frame", "Oil lantern"), listOf(OilLantern.LAMP, OilLantern.FRAME, OilLantern.LANTERN).map(::name))
    }

    @Test
    fun `glass is blown with the glassblowing pipe from molten glass`() {
        assertEquals(listOf("Glassblowing pipe", "Molten glass"), listOf(GlassMaterial.PIPE, GlassMaterial.MOLTEN_GLASS).map(::name))
    }
}
