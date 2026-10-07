package game.idle.flow.option

import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OptionFactsTest {

    private val facts = OptionFacts(levels = mapOf(Skill.WOODCUTTING to 30), bank = mapOf(1521 to 312))

    @Test
    fun `a skill the facts do not hold is at level 1`() {
        assertEquals(1, facts.level(Skill.MINING))
    }

    @Test
    fun `an item the bank does not hold counts 0`() {
        assertEquals(0, facts.banked(1519))
    }

    @Test
    fun `a level reached lacks nothing`() {
        assertNull(facts.lacks(Skill.WOODCUTTING, 30))
    }

    @Test
    fun `a level not reached names the skill and level needed`() {
        assertEquals("needs Woodcutting 31", facts.lacks(Skill.WOODCUTTING, 31))
    }

    @Test
    fun `the bank note gives the count`() {
        assertEquals("312 in bank", facts.bankNote(1521))
    }

    @Test
    fun `the bank note says when the bank holds none`() {
        assertEquals("none in bank", facts.bankNote(1519))
    }
}
