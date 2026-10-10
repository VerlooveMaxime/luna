package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReflexFormTest {

    private val trout = 333
    private val lobster = 379
    private val cake = 1891
    private val slice = 1895
    private val foods = OptionSource { listOf(StepOption("$trout", "Trout", OptionIcon.Item(trout))) }
    private val portions = mapOf(trout to setOf(trout), lobster to setOf(lobster), cake to setOf(cake, slice))
    private val types = StepTypes(listOf(FakeStepType("fight"), FakeStepType("bank")))
    private val form = ReflexForm(foods, portions, types, FakeNames(mapOf(trout to "Trout", lobster to "Lobster")))
    private val steps = listOf(step("fight").copy(id = 4), step("bank").copy(id = 7))

    private fun reflex(vararg values: Pair<String, String>) = ReflexSettings(2, mapOf(*values))

    private fun shown(vararg values: Pair<String, String>): List<String> {
        val settings = form.settings(reflex(*values))
        return described(form.fields(steps).filter { it.visible(settings) })
    }

    @Test
    fun `a reflex's screen edits it as settings and gives it back`() {
        val reflex = reflex("do" to "run")

        assertEquals(StepSettings(ReflexForm.KIND, mapOf("do" to "run"), id = 2), form.settings(reflex))
        assertEquals(reflex, form.reflex(form.settings(reflex)))
    }

    @Test
    fun `an eat reflex's screen shows Do, When and its food list`() {
        assertEquals(
            listOf(
                "Do (left): toggle do eat 'Eat' / run 'Run away'",
                "When (left): typed below 1..99",
                "Food (left): list foods on 4 rows, 'What would you like to eat?'",
            ),
            shown(),
        )
    }

    @Test
    fun `a reflex that runs away and stops shows Then instead of the food list`() {
        assertEquals(
            listOf("Do (left): toggle do eat 'Eat' / run 'Run away'", "When (left): typed below 1..99", "Then (left): toggle then stop 'Stop the flow' / jump 'Jump to a step'"),
            shown("do" to "run"),
        )
    }

    @Test
    fun `a reflex that jumps shows the step it jumps to`() {
        assertEquals("Step (left): search step, 'Which step should it jump to?'", shown("do" to "run", "then" to "jump").last())
    }

    @Test
    fun `the toggles light eat and stop the flow by default`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()

        assertEquals(listOf("eat", "stop"), toggles.map { it.current(StepSettings(), FlowContext()) })
    }

    @Test
    fun `the toggles light what the reflex holds`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()
        val settings = form.settings(reflex("do" to "run", "then" to "jump"))

        assertEquals(listOf("run", "jump"), toggles.map { it.current(settings, FlowContext()) })
    }

    @Test
    fun `When shows the share of hitpoints, half without one`() {
        val typed = form.fields(steps).filterIsInstance<StepField.Typed>().single()

        assertEquals(listOf("Hitpoints below 30%", "Hitpoints below 50%"), listOf(typed.shown("30"), typed.shown(null)))
    }

    @Test
    fun `an empty food list offers any food until some is picked`() {
        val list = form.fields(steps).filterIsInstance<StepField.Items>().single()

        assertEquals(listOf("+ Any food: pick some...", "+ Add or remove food..."), listOf(list.empty, list.add))
    }

    @Test
    fun `the step search offers the flow's steps`() {
        val search = form.fields(steps).filterIsInstance<StepField.Search>().single()

        assertEquals(listOf("4", "7"), search.target.source.options(OptionContext()).map { it.value })
    }

    @Test
    fun `an eat reflex is named, described and pictured by its first food`() {
        val eat = reflex("foods" to "$lobster,$trout")

        assertEquals(
            listOf("Eat", "Eats when hitpoints fall below a share of full.", StepIcon.Item(lobster)),
            listOf(form.label(eat), form.description(eat), form.icon(eat)),
        )
    }

    @Test
    fun `an eat reflex of any food shows shrimps`() {
        assertEquals(StepIcon.Item(ReflexForm.SHRIMPS), form.icon(reflex()))
    }

    @Test
    fun `a run away reflex is named, described and pictured by leather boots`() {
        val run = reflex("do" to "run")

        assertEquals(
            listOf("Run away", "Runs from what attacks you, then goes on or stops the flow.", StepIcon.Item(ReflexForm.LEATHER_BOOTS)),
            listOf(form.label(run), form.description(run), form.icon(run)),
        )
    }

    @Test
    fun `an eat reflex reads as the foods it eats`() {
        assertEquals("Hitpoints below 40%: eat trout, lobster", form.sentence(reflex("below" to "40", "foods" to "$trout,$lobster"), steps))
    }

    @Test
    fun `an eat reflex without foods eats any food below half hitpoints`() {
        assertEquals("Hitpoints below 50%: eat any food", form.sentence(reflex(), steps))
    }

    @Test
    fun `a run away reflex reads as running, then stopping the flow`() {
        assertEquals("Hitpoints below 50%: run away, then stop the flow", form.sentence(reflex("do" to "run"), steps))
    }

    @Test
    fun `a jump reads as the number its step stands at`() {
        assertEquals("Hitpoints below 50%: run away, then jump to step 2", form.sentence(reflex("do" to "run", "then" to "jump", "step" to "7"), steps))
    }

    @Test
    fun `a jump with no step picked, or a deleted one, says so`() {
        assertEquals(
            listOf("Hitpoints below 50%: run away, then jump to a step", "Hitpoints below 50%: run away, then jump to a deleted step"),
            listOf(form.sentence(reflex("do" to "run", "then" to "jump"), steps), form.sentence(reflex("do" to "run", "then" to "jump", "step" to "9"), steps)),
        )
    }

    @Test
    fun `an eat reflex needs one of its foods, any portion of it`() {
        assertEquals(ToolNeed("food", mapOf(cake to 0, slice to 0), countable = false), form.need(reflex("foods" to "$cake")))
    }

    @Test
    fun `an eat reflex of any food needs any food`() {
        assertEquals(setOf(trout, lobster, cake), form.need(reflex())?.items?.keys)
    }

    @Test
    fun `a food Luna cannot eat counts as none picked`() {
        assertEquals(setOf(trout, lobster, cake), form.need(reflex("foods" to "1511"))?.items?.keys)
    }

    @Test
    fun `a run away reflex needs nothing`() {
        assertNull(form.need(reflex("do" to "run")))
    }
}
