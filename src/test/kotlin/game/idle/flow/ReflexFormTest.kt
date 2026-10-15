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
        val reflex = reflex("do" to "stop")

        assertEquals(StepSettings(ReflexForm.KIND, mapOf("do" to "stop"), id = 2), form.settings(reflex))
        assertEquals(reflex, form.reflex(form.settings(reflex)))
    }

    @Test
    fun `an eat reflex's screen shows When and Below on the left, Do and its food list on the right`() {
        assertEquals(
            listOf(
                "When (left): toggle when hitpoints 'Hitpoints' / attacked 'Under attack'",
                "Below (left): typed below 1..99",
                "Do (right): toggle do eat 'Eat' / stop 'Stop' / jump 'Jump'",
                "Food (right): list foods on 4 rows, 'What would you like to eat?'",
            ),
            shown(),
        )
    }

    @Test
    fun `an under attack reflex has no Below`() {
        assertEquals(listOf("When", "Do", "Food"), shown("when" to "attacked").map { it.substringBefore(" (") })
    }

    @Test
    fun `a stop reflex shows When and Do alone`() {
        assertEquals(listOf("When", "Below", "Do"), shown("do" to "stop").map { it.substringBefore(" (") })
    }

    @Test
    fun `a reflex that jumps shows the step it jumps to`() {
        assertEquals("Step (right): search step, 'Which step should it jump to?'", shown("do" to "jump").last())
    }

    @Test
    fun `the toggles light hitpoints and eat by default`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()

        assertEquals(listOf("hitpoints", "eat"), toggles.map { it.current(StepSettings(), FlowContext()) })
    }

    @Test
    fun `the toggles light what the reflex holds`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()
        val settings = form.settings(reflex("when" to "attacked", "do" to "jump"))

        assertEquals(listOf("attacked", "jump"), toggles.map { it.current(settings, FlowContext()) })
    }

    @Test
    fun `Below shows the share of hitpoints, half without one`() {
        val typed = form.fields(steps).filterIsInstance<StepField.Typed>().single()

        assertEquals(listOf("30%", "50%"), listOf(typed.shown("30"), typed.shown(null)))
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
            listOf("Eat", "Eats the first of its foods the bag holds.", StepIcon.Item(lobster)),
            listOf(form.label(eat), form.description(eat), form.icon(eat)),
        )
    }

    @Test
    fun `an eat reflex of any food shows shrimps`() {
        assertEquals(StepIcon.Item(ReflexForm.SHRIMPS), form.icon(reflex()))
    }

    @Test
    fun `a stop reflex is named, described and pictured by the red cross`() {
        val stop = reflex("do" to "stop")

        assertEquals(
            listOf("Stop the flow", "Stops the flow, running clear first while attacked.", ReflexForm.STOP_ICON),
            listOf(form.label(stop), form.description(stop), form.icon(stop)),
        )
    }

    @Test
    fun `a jump reflex is named, described and pictured by the hint arrow`() {
        val jump = reflex("do" to "jump")

        assertEquals(
            listOf("Jump to a step", "Goes on from another step, running clear first while attacked.", ReflexForm.JUMP_ICON),
            listOf(form.label(jump), form.description(jump), form.icon(jump)),
        )
    }

    @Test
    fun `a reflex with a Do no reflex has shows as stopping`() {
        assertEquals(listOf("Stop the flow", ReflexForm.STOP_ICON), listOf(form.label(reflex("do" to "run")), form.icon(reflex("do" to "run"))))
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
    fun `a stop reflex reads as stopping the flow`() {
        assertEquals("Hitpoints below 50%: stop the flow", form.sentence(reflex("do" to "stop"), steps))
    }

    @Test
    fun `an under attack reflex reads as such`() {
        assertEquals("Under attack: stop the flow", form.sentence(reflex("when" to "attacked", "do" to "stop"), steps))
    }

    @Test
    fun `a jump reads as the number its step stands at`() {
        assertEquals("Hitpoints below 50%: jump to step 2", form.sentence(reflex("do" to "jump", "step" to "7"), steps))
    }

    @Test
    fun `a jump with no step picked, or a deleted one, says so`() {
        assertEquals(
            listOf("Hitpoints below 50%: jump to a step", "Hitpoints below 50%: jump to a deleted step"),
            listOf(form.sentence(reflex("do" to "jump"), steps), form.sentence(reflex("do" to "jump", "step" to "9"), steps)),
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
    fun `a stop reflex needs nothing`() {
        assertNull(form.need(reflex("do" to "stop")))
    }

    @Test
    fun `eating under attack warns that it eats at full hitpoints`() {
        assertEquals(listOf(ReflexForm.EATS_AT_FULL), form.warnings(reflex("when" to "attacked")))
    }

    @Test
    fun `eating on hitpoints and stopping under attack waste nothing`() {
        assertEquals(listOf(emptyList<String>(), emptyList()), listOf(form.warnings(reflex()), form.warnings(reflex("when" to "attacked", "do" to "stop"))))
    }

    @Test
    fun `the attach search offers the flow's reflexes by id, numbered sentences in the flow's order`() {
        val options = form.attachOptions(listOf(reflex("foods" to "$trout"), ReflexSettings(5, mapOf("do" to "jump", "step" to "7"))), steps)

        assertEquals(
            listOf(
                StepOption("2", "1 Hitpoints below 50%: eat trout", OptionIcon.Item(trout), group = 0),
                StepOption("5", "2 Hitpoints below 50%: jump to step 2", OptionIcon.Media("headicons_hint", 0), group = 1),
            ),
            options,
        )
    }
}
