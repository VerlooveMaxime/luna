package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StepSettingsTest {

    private val chop = StepSettings("chop", mapOf("tree" to "oak"))

    @Test
    fun `a setting is read by its name`() {
        assertEquals("oak", chop["tree"])
    }

    @Test
    fun `a setting the step has no value for reads as none`() {
        assertNull(chop["amount"])
    }

    @Test
    fun `setting a value adds or replaces it`() {
        assertEquals(StepSettings("chop", mapOf("tree" to "willow", "amount" to "5")), chop.with("tree", "willow").with("amount", "5"))
    }

    @Test
    fun `an empty value removes the setting`() {
        assertEquals(StepSettings("chop"), chop.with("tree", ""))
    }
}
