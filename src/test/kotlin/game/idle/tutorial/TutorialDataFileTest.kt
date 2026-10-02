package game.idle.tutorial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The tracked `tutorial.jsonc`, as the server loads it at boot. */
class TutorialDataFileTest {

    private val data = TutorialData.load(TutorialData.PATH)

    @Test
    fun `the tracked file holds every dialogue the script needs`() {
        TutorialScript(data)
    }

    @Test
    fun `every help line fits the help box`() {
        val tooLong = data.steps.values.flatMap { it.help.lines }.filter { it.length > MAX_HELP_LINE }

        assertEquals(emptyList<String>(), tooLong)
    }

    @Test
    fun `every dialogue line fits a dialogue box`() {
        val tooLong = data.dialogues.values.flatten().flatMap { it.lines }.filter { it.length > MAX_DIALOGUE_LINE }

        assertEquals(emptyList<String>(), tooLong)
    }

    private companion object {
        /** The help box is 480 pixels wide; LostCity breaks its text at 450. */
        const val MAX_HELP_LINE = 60

        /** About what Luna's own npc dialogues use; to check against the 3D client. */
        const val MAX_DIALOGUE_LINE = 55
    }
}
