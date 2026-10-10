package game.idle.flow.option

import game.idle.flow.FakeStepType
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepTypes
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JumpTargetOptionsTest {

    private val trees = StepTarget(FakeStepType.WORD, OptionSource { listOf(StepOption("oak", "Oak tree", OptionIcon.Item(1521))) })
    private val types = StepTypes(
        listOf(
            FakeStepType("chop", target = trees, icon = StepIcon.Skill(Skill.WOODCUTTING)),
            FakeStepType("bank", icon = StepIcon.Media("mapfunction", 5)),
            FakeStepType("eat", icon = StepIcon.Item(333)),
        ),
    )

    private fun options(vararg steps: StepSettings) = JumpTargetOptions(steps.toList(), types, FakeNames()).options(OptionContext())

    @Test
    fun `each step is offered by its id, named by its number and kind, over what it picked`() {
        val row = options(StepSettings("bank", id = 3), FakeStepType.step("chop", "oak").copy(id = 8))[1]

        assertEquals(StepOption("8", "Step 2: Chop label", OptionIcon.Skill(Skill.WOODCUTTING), note = "Oak tree", group = 1), row)
    }

    @Test
    fun `a step that picks nothing has no note`() {
        assertEquals("", options(StepSettings("bank", id = 3)).single().note)
    }

    @Test
    fun `a step shows its kind's icon, whatever sort it is`() {
        val icons = options(StepSettings("bank", id = 1), StepSettings("eat", id = 2)).map { it.icon }

        assertEquals(listOf(OptionIcon.Media("mapfunction", 5), OptionIcon.Item(333)), icons)
    }

    @Test
    fun `a step of no known kind is not offered`() {
        assertEquals(listOf("2"), options(StepSettings("dance", id = 1), StepSettings("bank", id = 2)).map { it.value })
    }
}
