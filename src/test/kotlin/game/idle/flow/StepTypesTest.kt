package game.idle.flow

import org.junit.jupiter.api.Assertions.assertFalse
import io.luna.game.model.mob.Skill
import game.idle.flow.option.ItemCatalog
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionFacts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepTypesTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val types = StepTypes(listOf(walk, rest))

    @Test
    fun `a kind of step is found by its name`() {
        assertSame(rest, types.find("rest"))
    }

    @Test
    fun `an unknown kind finds nothing`() {
        assertNull(types.find("fly"))
    }

    @Test
    fun `a flow needs a kind of step`() {
        assertThrows<IllegalArgumentException> { StepTypes(emptyList()) }
    }

    @Test
    fun `two kinds of step cannot share a name`() {
        assertThrows<IllegalArgumentException> { StepTypes(listOf(walk, FakeStepType("walk"))) }
    }

    @Test
    fun `a kind of step has no target unless it picks one`() {
        assertNull(rest.target(FakeNames()))
    }

    @Test
    fun `a kind of step shows no details unless it has some`() {
        assertEquals(emptyList<String>(), rest.details(step("rest"), FlowContext()))
    }

    @Test
    fun `a step's input is its kind's`() {
        val types = StepTypes(listOf(FakeStepType("light", inputOf = { InputSource.EARLIER_STEPS })))

        assertEquals(InputSource.EARLIER_STEPS, types.input(StepSettings("light"), FlowContext()))
    }

    @Test
    fun `a step of a kind no type knows takes from the bank`() {
        assertEquals(InputSource.BANK, StepTypes(listOf(FakeStepType("light"))).input(StepSettings("gone"), FlowContext()))
    }

    @Test
    fun `a step's own warnings are its kind's`() {
        val types = StepTypes(listOf(WarningStepType("pickup")))

        assertEquals(listOf("pickup warns"), types.warnings(StepSettings("pickup"), FlowContext(), OptionFacts()))
    }

    @Test
    fun `a kind without warnings of its own gives none`() {
        assertEquals(emptyList<String>(), types.warnings(step("walk"), FlowContext(), OptionFacts()))
    }

    @Test
    fun `a step of a kind no type knows has no warnings of its own`() {
        assertEquals(emptyList<String>(), types.warnings(StepSettings("gone"), FlowContext(), OptionFacts()))
    }

    private class WarningStepType(kind: String) : StepType by FakeStepType(kind), OwnWarnings {
        override fun warnings(settings: StepSettings, before: FlowContext, facts: OptionFacts): List<String> = listOf("${settings.kind} warns")
    }

    private val chop = FakeStepType("chop", listOf(StepField.MapTile("tile", "Tile")), skill = Skill.WOODCUTTING, goal = true)
    private val goaled = StepTypes(listOf(chop, walk), ItemCatalog(mapOf(1521 to "Oak logs")))

    @Test
    fun `a kind that takes a goal shows the Until rows before its own`() {
        assertEquals(
            listOf("Until", "Skill", "Level", "Count", "Minutes", "Item", "Count", "When met", "Tile"),
            goaled.fields(chop, FakeNames()).map { it.label },
        )
    }

    @Test
    fun `a kind without a goal shows its own rows alone`() {
        assertEquals(emptyList<StepField>(), goaled.fields(walk, FakeNames()))
    }

    @Test
    fun `a step's goal is read for a kind that takes one`() {
        val goal = goaled.goal(StepSettings("chop", mapOf("goal" to "bank", "goalItem" to "1521", "goalCount" to "10")))

        assertEquals(ResolvedGoal(Goal.Bank(1521, setOf(1521), 10), WhenMet.SKIP), goal)
    }

    @Test
    fun `a step of a kind without a goal has none, whatever it holds`() {
        assertNull(goaled.goal(StepSettings("walk", mapOf("goal" to "level", "goalLevel" to "20"))))
    }

    @Test
    fun `a step of a kind no type knows has no goal`() {
        assertNull(goaled.goal(StepSettings("gone", mapOf("goal" to "level"))))
    }

    @Test
    fun `without a catalog of items a bank goal names no item`() {
        val error = assertThrows<FlowError> { StepTypes(listOf(chop)).goal(StepSettings("chop", mapOf("goal" to "bank", "goalItem" to "1521", "goalCount" to "10"))) }

        assertEquals("'1521' is not an item", error.message)
    }

    @Test
    fun `a kind takes no goal unless it says so`() {
        assertFalse(walk.goal)
    }
}
