package game.idle.tutorial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TutorialStepTest {

    @Test
    fun `a saved value maps to its step`() {
        assertEquals(TutorialStep.OPEN_HOUSE_DOOR, TutorialStep.of(4))
    }

    @Test
    fun `a value between two steps maps to the earlier one`() {
        assertEquals(TutorialStep.OPEN_HOUSE_DOOR, TutorialStep.of(7))
    }

    @Test
    fun `a value past the last step maps to done`() {
        assertEquals(TutorialStep.DONE, TutorialStep.of(5000))
    }

    @Test
    fun `a negative value is refused`() {
        assertThrows<IllegalArgumentException> { TutorialStep.of(-1) }
    }
}
