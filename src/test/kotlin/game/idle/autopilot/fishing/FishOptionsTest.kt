package game.idle.autopilot.fishing

import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Luna's fish name themselves from the cache, so these run on [TestWorld]. */
class FishOptionsTest {

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    private fun rows() = FishOptions(LunaGameNames).options(OptionContext(OptionFacts(levels = mapOf(Skill.FISHING to 20))))

    private fun row(value: String): StepOption = rows().single { it.value == value }

    @Test
    fun `every fish of every method is a row, under the word its setting keeps`() {
        assertEquals(FishingCatch.ALL.map { it.word }, rows().map { it.value })
    }

    @Test
    fun `a row is named after its fish and the tool that catches it`() {
        assertEquals("Trout (fly fishing rod)", row("trout").label)
    }

    @Test
    fun `a method's other fish is a row of its own`() {
        assertEquals("Anchovies (small fishing net)", row("anchovy").label)
    }

    @Test
    fun `the big net's caskets and seaweed are no rows`() {
        assertEquals(listOf("mackerel", "cod", "bass"), rows().filter { "big fishing net" in it.label }.map { it.value })
    }

    @Test
    fun `the note gives the fish's level`() {
        assertEquals("Fishing 15", row("anchovy").note)
    }

    @Test
    fun `a row shows its fish`() {
        assertEquals(OptionIcon.Item(335), row("trout").icon)
    }

    @Test
    fun `a fish at the player's level can be picked`() {
        assertNull(row("trout").blocked)
    }

    @Test
    fun `a fish above the player's level is greyed on its own level, not its tool's`() {
        assertEquals("needs Fishing 30", row("salmon").blocked)
    }
}
