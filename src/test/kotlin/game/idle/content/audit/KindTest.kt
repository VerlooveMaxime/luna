package game.idle.content.audit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KindTest {

    @Test
    fun `an npc with Attack on option 2 is attackable`() {
        assertTrue(npc(100, "", "Attack").attackable)
    }

    @Test
    fun `an npc with another label on option 2 is not attackable`() {
        assertFalse(npc(43, "", "Shear").attackable)
    }

    @Test
    fun `Attack on another npc option does not start combat`() {
        assertFalse(npc(100, "Attack").startsCombat(0))
    }

    @Test
    fun `no object option starts combat`() {
        assertFalse(obj(2000, "", "Attack").startsCombat(1))
    }

    @Test
    fun `strength 23 gives a max hit of 3`() {
        assertEquals(3, stats(strength = 23).strengthMaxHit)
    }

    @Test
    fun `strength 300 gives a max hit of 31`() {
        assertEquals(31, stats(strength = 300).strengthMaxHit)
    }

    @Test
    fun `one skill above 0 is enough for a filled-in definition`() {
        assertFalse(stats(strength = 0, otherSkills = 0).copy(magic = 1).everySkillZero)
    }
}
