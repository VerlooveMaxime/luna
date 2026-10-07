package game.idle.flow.option

import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SkillOptionsTest {

    @Test
    fun `every skill is a row with the player's level`() {
        val rows = SkillOptions.options(OptionContext(OptionFacts(levels = mapOf(Skill.SLAYER to 7))))

        assertEquals(StepOption("slayer", "Slayer", OptionIcon.Skill(Skill.SLAYER), "level 7"), rows[Skill.SLAYER])
    }

    @Test
    fun `the rows are the skills of the skills tab`() {
        assertEquals(21, SkillOptions.options(OptionContext()).size)
    }
}
