package game.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HeadlessNameTest {

    @Test
    fun `a prefixed name is accepted in lower case`() {
        assertEquals("agent_wc1", requireHeadlessName(" Agent_WC1 ", prefix = "agent_"))
    }

    @Test
    fun `a name of exactly twelve characters is accepted`() {
        assertEquals("agent_abcdef", requireHeadlessName("agent_abcdef", prefix = "agent_"))
    }

    @Test
    fun `a name longer than twelve characters is refused`() {
        val thrown = assertThrows<HarnessException> { requireHeadlessName("agent_abcdefg", prefix = "agent_") }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `a name with a space is refused`() {
        assertThrows<HarnessException> { requireHeadlessName("agent_a b", prefix = "agent_") }
    }

    @Test
    fun `an empty name is refused`() {
        assertThrows<HarnessException> { requireHeadlessName("", prefix = "agent_") }
    }

    @Test
    fun `a name without the prefix is refused`() {
        val thrown = assertThrows<HarnessException> { requireHeadlessName("wc1", prefix = "agent_") }

        assertEquals("headless player names must start with 'agent_'", thrown.message)
    }
}
