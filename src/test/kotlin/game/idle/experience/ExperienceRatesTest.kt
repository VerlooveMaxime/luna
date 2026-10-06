package game.idle.experience

import game.idle.IdleState
import game.idle.idleState
import game.idle.tutorial.TutorialStep
import game.idle.tutorial.tutorialStep
import game.idle.tutorial.TutorialExperience
import game.testworld.TestWorld
import io.luna.Luna
import io.luna.game.model.Position
import io.luna.game.model.mob.ExperienceModifier
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class ExperienceRatesTest {

    private val rates = ExperienceRates(idleRate = 0.1, activeRate = 0.3)
    private val multiplier = Luna.settings().game().experienceMultiplier()

    @AfterEach
    fun resetWorld() {
        TestWorld.world.experienceModifier = ExperienceModifier.NONE
        TestWorld.reset()
    }

    @Test
    fun `the idle rate applies while the autopilot runs, the active rate otherwise`() {
        assertEquals(listOf(0.1, 0.3), listOf(rates.rate(idle = true), rates.rate(idle = false)))
    }

    @Test
    fun `the tracked data file holds the rates Maxime picked`() {
        assertEquals(ExperienceRates(idleRate = 0.10, activeRate = 0.30), ExperienceRates.load(ExperienceRates.PATH))
    }

    @Test
    fun `a rate must be positive`(@TempDir dir: Path) {
        val idle = Files.writeString(dir.resolve("idle.jsonc"), """{ "idle_rate": 0, "active_rate": 0.3 }""")
        val active = Files.writeString(dir.resolve("active.jsonc"), """{ "idle_rate": 0.1, "active_rate": -1 }""")

        assertEquals("idle_rate must be positive, got 0.0", assertThrows<IllegalArgumentException> { ExperienceRates.load(idle) }.message)
        assertEquals("active_rate must be positive, got -1.0", assertThrows<IllegalArgumentException> { ExperienceRates.load(active) }.message)
    }

    @Test
    fun `a player playing themselves gains the active share`() {
        val player = TestWorld.login("rated", Position(3200, 3200))
        TestWorld.world.experienceModifier = RatedExperience(rates, ExperienceModifier.NONE)

        player.skills.getSkill(Skill.WOODCUTTING).addExperience(100.0)

        assertEquals(30.0 * multiplier, player.skills.getSkill(Skill.WOODCUTTING).experience, 1e-9)
    }

    @Test
    fun `a player whose autopilot runs gains the idle share`() {
        val player = TestWorld.login("rated", Position(3200, 3200))
        player.idleState = IdleState(running = true)
        TestWorld.world.experienceModifier = RatedExperience(rates, ExperienceModifier.NONE)

        player.skills.getSkill(Skill.WOODCUTTING).addExperience(100.0)

        assertEquals(10.0 * multiplier, player.skills.getSkill(Skill.WOODCUTTING).experience, 1e-9)
    }

    @Test
    fun `the rated gain goes through the next modifier, such as the island cap`() {
        val player = TestWorld.login("rated", Position(3200, 3200))
        player.tutorialStep = TutorialStep.CUT_TREE
        player.skills.getSkill(Skill.WOODCUTTING).experience = 200.0
        TestWorld.world.experienceModifier = RatedExperience(rates, TutorialExperience())
        val before = player.skills.getSkill(Skill.WOODCUTTING).experience

        player.skills.getSkill(Skill.WOODCUTTING).addExperience(100.0)

        assertEquals(before, player.skills.getSkill(Skill.WOODCUTTING).experience, 1e-9)
    }
}
