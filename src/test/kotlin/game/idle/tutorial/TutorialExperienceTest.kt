package game.idle.tutorial

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TutorialExperienceTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a skill below level 3 keeps gaining on the island`() {
        assertFalse(TutorialExperience.capped(TutorialStep.CUT_TREE, staticLevel = 2))
    }

    @Test
    fun `a skill at level 3 stops gaining on the island`() {
        assertTrue(TutorialExperience.capped(TutorialStep.CUT_TREE, staticLevel = 3))
    }

    @Test
    fun `a skill keeps gaining past level 3 once the tutorial is done`() {
        assertFalse(TutorialExperience.capped(TutorialStep.DONE, staticLevel = 3))
    }

    @Test
    fun `a player on the island at level 3 is granted nothing`() {
        val player = TestWorld.login("tutee", Position(3200, 3200))
        player.tutorialStep = TutorialStep.CUT_TREE
        player.skills.getSkill(Skill.WOODCUTTING).experience = SkillSet.experienceForLevel(3).toDouble()

        assertEquals(0.0, TutorialExperience().modify(player, Skill.WOODCUTTING, 25.0))
    }

    @Test
    fun `a player below level 3 on the island is granted the whole amount`() {
        val player = TestWorld.login("tutee", Position(3200, 3200))
        player.tutorialStep = TutorialStep.CUT_TREE

        assertEquals(25.0, TutorialExperience().modify(player, Skill.WOODCUTTING, 25.0))
    }
}
