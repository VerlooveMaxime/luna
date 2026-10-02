package io.luna.game.model.mob

import game.testworld.TestWorld
import io.luna.Luna
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The IdleRS hook in [Skill.addExperience]: a player's gain goes through the world's [ExperienceModifier]. */
class SkillExperienceModifierTest {

    private val doubling = ExperienceModifier { _, _, amount -> amount * 2 }

    @AfterEach
    fun resetWorld() {
        TestWorld.world.experienceModifier = ExperienceModifier.NONE
        TestWorld.reset()
    }

    private fun woodcutting(mob: Mob): Skill = mob.skills.getSkill(Skill.WOODCUTTING)

    @Test
    fun `a player's gain goes through the world's modifier`() {
        val player = TestWorld.login("gainer", Position(3200, 3200))
        TestWorld.world.experienceModifier = doubling

        woodcutting(player).addExperience(10.0)

        assertEquals(20.0 * Luna.settings().game().experienceMultiplier(), woodcutting(player).experience)
    }

    @Test
    fun `nothing is granted when the modifier leaves nothing`() {
        val player = TestWorld.login("gainer", Position(3200, 3200))
        TestWorld.world.experienceModifier = ExperienceModifier { _, _, _ -> 0.0 }

        woodcutting(player).addExperience(10.0)

        assertEquals(0.0, woodcutting(player).experience)
    }

    @Test
    fun `a bot's gain is left alone`() {
        val bot = TestWorld.bot("gainerbot", Position(3200, 3200))
        TestWorld.world.experienceModifier = doubling

        woodcutting(bot).addExperience(10.0)

        assertEquals(10.0, woodcutting(bot).experience)
    }

    @Test
    fun `an npc's gain is left alone`() {
        val npc = TestWorld.spawnNpc(1, Position(3200, 3200))
        TestWorld.world.experienceModifier = doubling

        woodcutting(npc).addExperience(10.0)

        assertEquals(10.0 * Luna.settings().game().experienceMultiplier(), woodcutting(npc).experience)
    }

    @Test
    fun `a gain of nothing is ignored before any modifier`() {
        val player = TestWorld.login("gainer", Position(3200, 3200))
        TestWorld.world.experienceModifier = ExperienceModifier { _, _, _ -> 50.0 }

        woodcutting(player).addExperience(0.0)

        assertEquals(0.0, woodcutting(player).experience)
    }
}
