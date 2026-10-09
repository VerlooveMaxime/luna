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
    fun `every fishing method is a row, under the word its setting keeps`() {
        assertEquals(FishingMethod.ALL.map { it.word }, rows().map { it.value })
    }

    @Test
    fun `a row is named after its fish and its tool`() {
        assertEquals("Trout, salmon (fly fishing rod)", row("trout").label)
    }

    @Test
    fun `a row names only raw fish, not the big net's caskets and seaweed`() {
        assertEquals("Mackerel, cod, bass (big fishing net)", row("mackerel").label)
    }

    @Test
    fun `the note gives the tool's level and the levels of the fish that come later`() {
        assertEquals("Fishing 5 (herring 10, pike 25)", row("sardine").note)
    }

    @Test
    fun `a method with a single fish notes only the tool's level`() {
        assertEquals("Fishing 40", row("lobster").note)
    }

    @Test
    fun `a row shows its first fish`() {
        assertEquals(OptionIcon.Item(335), row("trout").icon)
    }

    @Test
    fun `a method at the player's level can be picked`() {
        assertNull(row("trout").blocked)
    }

    @Test
    fun `a method above the player's level is greyed on the tool's level`() {
        assertEquals("needs Fishing 35", row("tuna").blocked)
    }
}
