package game.idle.flow.option

import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StepTargetTest {

    private val asked = mutableListOf<OptionContext>()

    /** Offers an oak and a willow, recording what it was asked with. */
    private val trees = OptionSource { context ->
        asked += context
        listOf(row("oak"), row("willow"))
    }

    private fun chop(tree: String? = null) = StepSettings("chop", tree?.let { mapOf("tree" to it) } ?: emptyMap())

    @Test
    fun `the option picked is the one whose value the setting holds`() {
        assertEquals("willow", StepTarget("tree", trees).picked(chop("willow"))?.label)
    }

    @Test
    fun `a step without the setting picks the target's default`() {
        assertEquals("oak", StepTarget("tree", trees, default = "oak").picked(chop())?.label)
    }

    @Test
    fun `a step without the setting and no default picks nothing`() {
        assertNull(StepTarget("tree", trees).picked(chop()))
    }

    @Test
    fun `a value no option has picks nothing`() {
        assertNull(StepTarget("tree", trees).picked(chop("palm")))
    }

    @Test
    fun `the options are asked for as from the bank, so a processing source offers all of them`() {
        StepTarget("tree", trees).picked(chop("oak"))

        assertEquals(InputSource.BANK, asked.single().input)
    }

    @Test
    fun `nothing is asked when nothing is picked`() {
        StepTarget("tree", trees).picked(chop())

        assertEquals(emptyList<OptionContext>(), asked)
    }
}
