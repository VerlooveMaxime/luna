package game.idle.flow.option

import game.idle.flow.StepIcon
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OptionIconTest {

    @Test
    fun `a step's icon shows in a search row as the same sort of picture`() {
        val icons = listOf(StepIcon.Skill(Skill.FISHING), StepIcon.Media("mapfunction", 5), StepIcon.Item(333)).map(OptionIcon::of)

        assertEquals(listOf(OptionIcon.Skill(Skill.FISHING), OptionIcon.Media("mapfunction", 5), OptionIcon.Item(333)), icons)
    }
}
