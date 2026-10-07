package game.idle.flow.option

import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GatherOptionsTest {

    @Test
    fun `a gathering row shows the skill and level it needs`() {
        val row = GatherOptions.option(OptionContext(), "willow", "Willow", 1519, Skill.WOODCUTTING, 30)

        assertEquals(
            StepOption("willow", "Willow", OptionIcon.Item(1519), "Woodcutting 30", "needs Woodcutting 30", level = 30),
            row,
        )
    }

    @Test
    fun `a gathering row at the player's level can be picked`() {
        val context = OptionContext(OptionFacts(levels = mapOf(Skill.WOODCUTTING to 30)))

        assertEquals(null, GatherOptions.option(context, "willow", "Willow", 1519, Skill.WOODCUTTING, 30).blocked)
    }
}
