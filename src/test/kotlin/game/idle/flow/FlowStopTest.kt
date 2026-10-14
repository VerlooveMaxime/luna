package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowStopTest {

    private fun refusal(settings: StopSettings): String? = assertThrows<FlowError> { FlowStops.read(settings) }.message

    @Test
    fun `a flow stops never by default`() {
        assertEquals(FlowStop.Never, FlowStops.read(StopSettings()))
    }

    @Test
    fun `a flow stops after the laps typed`() {
        assertEquals(FlowStop.AfterLaps(5), FlowStops.read(StopSettings("laps", "5")))
    }

    @Test
    fun `a flow stops after the minutes typed`() {
        assertEquals(FlowStop.AfterMinutes(30), FlowStops.read(StopSettings("time", "30")))
    }

    @Test
    fun `a flow stops when all goals are met`() {
        assertEquals(FlowStop.AllGoalsMet, FlowStops.read(StopSettings("goals", "7")))
    }

    @Test
    fun `laps and minutes are needed, 1 or more`() {
        assertEquals("Needs laps", refusal(StopSettings("laps", "")))
        assertEquals("Laps take 1 or more, not '0'", refusal(StopSettings("laps", "0")))
        assertEquals("Needs minutes", refusal(StopSettings("time", "")))
        assertEquals("Minutes take 1 or more, not 'ten'", refusal(StopSettings("time", "ten")))
    }

    @Test
    fun `a kind of stop that is none is refused`() {
        assertEquals("'death' is not a kind of stop", refusal(StopSettings("death", "")))
    }

    @Test
    fun `only after laps and after time take a number`() {
        assertTrue(FlowStops.typed("laps"))
        assertTrue(FlowStops.typed("time"))
        assertFalse(FlowStops.typed("never"))
        assertFalse(FlowStops.typed("goals"))
    }

    @Test
    fun `the box words the number with its unit, big ones as a stack`() {
        assertEquals(listOf("5 laps", "150K min"), listOf(FlowStops.afterWords(StopSettings("laps", "5")), FlowStops.afterWords(StopSettings("time", "150000"))))
    }

    @Test
    fun `the box has no words without a number or for a stop without one`() {
        assertNull(FlowStops.afterWords(StopSettings("laps", "")))
        assertNull(FlowStops.afterWords(StopSettings("goals", "5")))
    }

    @Test
    fun `the row's choices are Never, After laps, After time and When all goals are met`() {
        assertEquals(listOf("Never", "After laps", "After time", "When all goals are met"), FlowStops.CHOICES.map { it.word })
        assertEquals(listOf("never", "laps", "time", "goals"), FlowStops.CHOICES.map { it.value })
    }
}
