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
    private val form = ReflexForm(foods, portions, types, FakeNames(mapOf(trout to "Trout", lobster to "Lobster", 113 to "Strength potion(4)", 2440 to "Super strength(4)")), OptionSource { emptyList() }, mapOf(113 to listOf(119, 117, 115, 113), 2440 to listOf(161, 159, 157, 2440)))
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
                "When (left): toggle when hitpoints 'Hitpoints' / skill 'Skill'",
                " (left): toggle when attacked 'Under attack'",
                "Below (left): typed below 1..99",
                "Do (right): toggle do eat 'Eat' / drink 'Drink' / stop 'Stop' / jump 'Jump'",
                "Food (right): list foods on 4 rows, 'What would you like to eat?'",
            ),
            shown(),
        )
    }

    @Test
    fun `an under attack reflex has no Below`() {
        assertEquals(listOf("When", "", "Do", "Food"), shown("when" to "attacked").map { it.substringBefore(" (") })
    }

    @Test
    fun `a stop reflex shows When and Do alone`() {
        assertEquals(listOf("When", "", "Below", "Do"), shown("do" to "stop").map { it.substringBefore(" (") })
    }

    @Test
    fun `a reflex that jumps shows the step it jumps to`() {
        assertEquals("Step (right): search step, 'Which step should it jump to?'", shown("do" to "jump").last())
    }

    @Test
    fun `the toggles light hitpoints and eat by default`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()

        assertEquals(listOf("hitpoints", "hitpoints", "eat"), toggles.map { it.current(StepSettings(), FlowContext()) })
    }

    @Test
    fun `the toggles light what the reflex holds`() {
        val toggles = form.fields(steps).filterIsInstance<StepField.Toggle>()
        val settings = form.settings(reflex("when" to "attacked", "do" to "jump"))

        assertEquals(listOf("attacked", "attacked", "jump"), toggles.map { it.current(settings, FlowContext()) })
    }

    @Test
    fun `Below shows the share of hitpoints, half without one`() {
        val typed = form.fields(steps).filterIsInstance<StepField.Typed>().single { it.key == ReflexKeys.BELOW }

        assertEquals(listOf("30%", "50%"), listOf(typed.shown("30"), typed.shown(null)))
    }

    @Test
    fun `an empty food list offers any food until some is picked`() {
        val list = form.fields(steps).filterIsInstance<StepField.Items>().single { it.key == ReflexKeys.FOODS }

        assertEquals(listOf("+ Any food: pick some...", "+ Add or remove food..."), listOf(list.empty, list.add))
    }

    @Test
    fun `the step search offers the flow's steps`() {
        val search = form.fields(steps).filterIsInstance<StepField.Search>().single { it.target.key == ReflexKeys.STEP }

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

    @Test
    fun `a skill condition has its search and its own Below level`() {
        assertEquals(listOf("When", "", "Skill", "Below", "Do", "Potions"), shown("when" to "skill", "do" to "drink").map { it.substringBefore(" (") })
    }

    @Test
    fun `the skill search offers every skill except hitpoints with its icon`() {
        val search = form.fields(steps).filterIsInstance<StepField.Search>().single { it.target.key == ReflexKeys.SKILL_NAME }
        val rows = search.target.source.options(OptionContext())

        assertEquals(io.luna.game.model.mob.Skill.NAMES.filter { it != "Hitpoints" }, rows.map { it.label })
        assertEquals(OptionIcon.Skill(5), rows.single { it.value == "prayer" }.icon)
    }

    @Test
    fun `the skill Below shows the typed level or that none is set`() {
        val field = form.fields(steps).filterIsInstance<StepField.Typed>().single { it.key == ReflexKeys.SKILL_BELOW }

        assertEquals(listOf("25", "@gry@not set yet"), listOf(field.shown("25"), field.shown(null)))
    }

    @Test
    fun `drink names its action and pictures the first potion`() {
        val drink = reflex("do" to "drink", "potions" to "2440,113")

        assertEquals(listOf("Drink", "Drinks a dose of the first of its potions the bag holds.", StepIcon.Item(2440)), listOf(form.label(drink), form.description(drink), form.icon(drink)))
    }

    @Test
    fun `a drink with no potion has an empty vial and says a potion`() {
        val drink = reflex("do" to "drink")

        assertEquals(listOf(StepIcon.Item(229), "Hitpoints below 50%: drink a potion"), listOf(form.icon(drink), form.sentence(drink, steps)))
    }

    @Test
    fun `a skill drink sentence names the skill and potion kinds without dose suffixes`() {
        assertEquals("Strength below 25: drink super strength, strength potion", form.sentence(reflex("when" to "skill", "skillName" to "strength", "skillBelow" to "25", "do" to "drink", "potions" to "2440,113"), steps))
    }

    @Test
    fun `a skill condition with unset values says what remains to set`() {
        assertEquals("Skill below not set yet: eat any food", form.sentence(reflex("when" to "skill"), steps))
    }

    @Test
    fun `an unknown skill stays visible so the bad value can be corrected`() {
        assertEquals("flying below 20: eat any food", form.sentence(reflex("when" to "skill", "skillName" to "flying", "skillBelow" to "20"), steps))
    }

    @Test
    fun `drinking needs any dose of its selected potions`() {
        assertEquals(ToolNeed("potion", mapOf(119 to 0, 117 to 0, 115 to 0, 113 to 0)), form.need(reflex("do" to "drink", "potions" to "113")))
    }

    @Test
    fun `an empty or invalid potion list never falls back to any potion`() {
        assertEquals(listOf(emptySet<Int>(), emptySet()), listOf(form.need(reflex("do" to "drink"))?.items?.keys, form.need(reflex("do" to "drink", "potions" to "1511"))?.items?.keys))
    }

    @Test
    fun `drinking under attack warns about spending a dose each time`() {
        assertEquals(listOf(ReflexForm.DRINKS_WHILE_ATTACKED), form.warnings(reflex("when" to "attacked", "do" to "drink")))
    }

    @Test
    fun `eating on a skill condition warns about eating at full hitpoints`() {
        assertEquals(listOf(ReflexForm.EATS_ON_SKILL), form.warnings(reflex("when" to "skill", "do" to "eat")))
    }

}
