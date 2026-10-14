package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.ItemCatalog
import game.idle.ui.ConfigureRows
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepGoalTest {

    private val oakLogs = 1521
    private val oakLogsNoted = 1522
    private val names = FakeNames(mapOf(oakLogs to "Oak logs"))
    private val form = GoalForm(ItemCatalog(mapOf(oakLogs to "Oak logs", 1511 to "Logs"), notes = mapOf(oakLogsNoted to oakLogs)))

    private val chop = FakeStepType("chop", skill = Skill.WOODCUTTING, goal = true)
    private val pickup = FakeStepType("pickup", goal = true)

    /** A kind whose skill follows what it makes, as the make step's does. */
    private val make = object : StepType by FakeStepType("make", goal = true) {
        override fun skill(settings: StepSettings): Int = if (settings["product"] == "bow") Skill.FLETCHING else Skill.CRAFTING
    }

    private fun goal(vararg values: Pair<String, String>): StepSettings = StepSettings("chop", mapOf(*values))

    private fun read(settings: StepSettings, type: StepType = chop): ResolvedGoal? = form.read(settings, type)

    private fun refusal(settings: StepSettings, type: StepType = chop): String? = assertThrows<FlowError> { form.read(settings, type) }.message

    @Test
    fun `a step without a goal has none`() {
        assertNull(read(goal()))
    }

    @Test
    fun `a level goal takes the skill picked and the level typed`() {
        assertEquals(ResolvedGoal(Goal.Level(Skill.STRENGTH, 20), WhenMet.SKIP), read(goal("goal" to "level", "goalSkill" to "Strength", "goalLevel" to "20")))
    }

    @Test
    fun `a level goal without a skill picked takes the kind's own`() {
        assertEquals(Goal.Level(Skill.WOODCUTTING, 45), read(goal("goal" to "level", "goalLevel" to "45"))?.goal)
    }

    @Test
    fun `a kind without a skill of its own needs one picked`() {
        assertEquals("Needs a skill", refusal(goal("goal" to "level", "goalLevel" to "45"), pickup))
    }

    @Test
    fun `a skill no skill is named is refused`() {
        assertEquals("'flying' is not a skill", refusal(goal("goal" to "level", "goalSkill" to "flying", "goalLevel" to "45")))
    }

    @Test
    fun `a level goal needs a level`() {
        assertEquals("Needs a level", refusal(goal("goal" to "level")))
    }

    @Test
    fun `a level is 1 to 99`() {
        assertEquals("Level takes 1 to 99, not '100'", refusal(goal("goal" to "level", "goalLevel" to "100")))
        assertEquals("Level takes 1 to 99, not '0'", refusal(goal("goal" to "level", "goalLevel" to "0")))
        assertEquals("Level takes 1 to 99, not 'max'", refusal(goal("goal" to "level", "goalLevel" to "max")))
    }

    @Test
    fun `a count goal takes the total typed`() {
        assertEquals(Goal.Count(100), read(goal("goal" to "count", "goalCount" to "100"))?.goal)
    }

    @Test
    fun `a count goal needs a count of 1 or more`() {
        assertEquals("Needs a count", refusal(goal("goal" to "count")))
        assertEquals("Count takes 1 or more, not '0'", refusal(goal("goal" to "count", "goalCount" to "0")))
    }

    @Test
    fun `a time goal takes the minutes typed`() {
        assertEquals(Goal.Time(30), read(goal("goal" to "time", "goalMinutes" to "30"))?.goal)
    }

    @Test
    fun `a time goal needs minutes, 1 or more`() {
        assertEquals("Needs minutes", refusal(goal("goal" to "time")))
        assertEquals("Minutes take 1 or more, not '0'", refusal(goal("goal" to "time", "goalMinutes" to "0")))
    }

    @Test
    fun `a bank goal takes the item picked, every item of its name, and the count typed`() {
        val goal = read(goal("goal" to "bank", "goalItem" to "1521", "goalCount" to "100"))?.goal

        assertEquals(Goal.Bank(oakLogs, setOf(oakLogs, oakLogsNoted), 100), goal)
    }

    @Test
    fun `a bank goal needs an item that is one`() {
        assertEquals("Needs an item", refusal(goal("goal" to "bank", "goalCount" to "100")))
        assertEquals("'999' is not an item", refusal(goal("goal" to "bank", "goalItem" to "999", "goalCount" to "100")))
        assertEquals("'logs' is not an item", refusal(goal("goal" to "bank", "goalItem" to "logs", "goalCount" to "100")))
    }

    @Test
    fun `a bank goal needs a count`() {
        assertEquals("Needs a count", refusal(goal("goal" to "bank", "goalItem" to "1521")))
    }

    @Test
    fun `a goal of a kind no goal has is refused`() {
        assertEquals("'xp' is not a kind of goal", refusal(goal("goal" to "xp")))
    }

    @Test
    fun `a goal skips its step when met unless told to stop the flow`() {
        val skip = read(goal("goal" to "count", "goalCount" to "1"))?.whenMet
        val stop = read(goal("goal" to "count", "goalCount" to "1", "goalMet" to "stop"))?.whenMet

        assertEquals(listOf(WhenMet.SKIP, WhenMet.STOP), listOf(skip, stop))
    }

    @Test
    fun `a choice for When met that is none is refused`() {
        assertEquals("'pause' is not a choice for When met", refusal(goal("goal" to "count", "goalCount" to "1", "goalMet" to "pause")))
    }

    @Test
    fun `the rows are Until, the skill and level, the count, the minutes, the item and its count, then When met, all on the right`() {
        assertEquals(
            listOf(
                "Until (right): toggle goal  'None' / level 'Level' / count 'Count' / time 'Time' / bank 'Bank'",
                "Skill (right): search goalSkill, 'Which skill?'",
                "Level (right): typed goalLevel 1..99",
                "Count (right): typed goalCount 1..2147483647",
                "Minutes (right): typed goalMinutes 1..2147483647",
                "Item (right): search goalItem, 'Which item in the bank?'",
                "Count (right): typed goalCount 1..2147483647",
                "When met (right): toggle goalMet skip 'Skip this step' / stop 'Stop the flow'",
            ),
            described(form.fields(chop)),
        )
    }

    @Test
    fun `only the Until row shows without a goal`() {
        assertEquals(listOf("Until"), ConfigureRows.shown(form.fields(chop), goal()).map { it.label })
    }

    @Test
    fun `a level goal shows its skill and level, then When met`() {
        assertEquals(listOf("Until", "Skill", "Level", "When met"), ConfigureRows.shown(form.fields(chop), goal("goal" to "level")).map { it.label })
    }

    @Test
    fun `a count goal shows its count`() {
        assertEquals(listOf("Until", "Count", "When met"), ConfigureRows.shown(form.fields(chop), goal("goal" to "count")).map { it.label })
    }

    @Test
    fun `a time goal shows its minutes`() {
        assertEquals(listOf("Until", "Minutes", "When met"), ConfigureRows.shown(form.fields(chop), goal("goal" to "time")).map { it.label })
    }

    @Test
    fun `a bank goal shows its item and count`() {
        assertEquals(listOf("Until", "Item", "Count", "When met"), ConfigureRows.shown(form.fields(chop), goal("goal" to "bank")).map { it.label })
    }

    @Test
    fun `the Until toggle lights none without a goal and the goal's kind with one`() {
        val toggle = form.fields(chop).first() as StepField.Toggle

        assertEquals(listOf("", "bank"), listOf(toggle.current(goal(), FlowContext()), toggle.current(goal("goal" to "bank"), FlowContext())))
    }

    @Test
    fun `the When met toggle lights skip by default and what the step holds`() {
        val toggle = form.fields(chop).last() as StepField.Toggle

        assertEquals(listOf("skip", "stop"), listOf(toggle.current(goal(), FlowContext()), toggle.current(goal("goalMet" to "stop"), FlowContext())))
    }

    @Test
    fun `the skill row picks the kind's own skill until another is picked`() {
        val skill = form.fields(chop)[1] as StepField.Search

        assertEquals(listOf("Woodcutting", "Strength"), listOf(skill.target.picked(goal())?.label, skill.target.picked(goal("goalSkill" to "strength"))?.label))
    }

    @Test
    fun `the skill row follows a kind whose skill follows its settings`() {
        val skill = form.fields(make)[1] as StepField.Search

        assertEquals(listOf("Crafting", "Fletching"), listOf(skill.target.picked(step("make"))?.label, skill.target.picked(StepSettings("make", mapOf("product" to "bow")))?.label))
    }

    @Test
    fun `the skill row of a kind without a skill picks nothing until one is`() {
        assertNull((form.fields(pickup)[1] as StepField.Search).target.picked(step("pickup")))
    }

    @Test
    fun `the typed rows word their value and say when none is typed`() {
        val typed = form.fields(chop).filterIsInstance<StepField.Typed>()

        assertEquals(
            listOf("level 45", "100 in all", "30 min", "150K in the bank"),
            listOf(typed[0].shown("45"), typed[1].shown("100"), typed[2].shown("30"), typed[3].shown("150000")),
        )
    }

    @Test
    fun `every typed row says when nothing is typed`() {
        assertEquals(List(4) { "@gry@not set yet" }, form.fields(chop).filterIsInstance<StepField.Typed>().map { it.shown(null) })
    }

    @Test
    fun `the words of a goal are short, as a line under the step, and as met`() {
        assertEquals(
            listOf("Strength 20", "until Strength 20", "Goal met: Strength 20"),
            words(Goal.Level(Skill.STRENGTH, 20)),
        )
    }

    @Test
    fun `a count goal counts in all`() {
        assertEquals(listOf("150K in all", "until 150K in all", "Goal met: 150K in all"), words(Goal.Count(150_000)))
    }

    @Test
    fun `a time goal is for minutes`() {
        assertEquals(listOf("30 min", "for 30 min", "Goal met: 30 min"), words(Goal.Time(30)))
    }

    @Test
    fun `a bank goal names its item`() {
        assertEquals(
            listOf("100 oak logs in the bank", "until the bank has 100 oak logs", "Goal met: 100 oak logs in the bank"),
            words(Goal.Bank(oakLogs, setOf(oakLogs), 100)),
        )
    }

    @Test
    fun `the met line starts with Goal met`() {
        assertEquals("Goal met: ", GoalWords.MET)
    }

    @Test
    fun `the form reads the same settings the rows keep`() {
        assertSame(GoalForm.KEY, (form.fields(chop).first() as StepField.Toggle).key)
    }

    private fun words(goal: Goal): List<String> = listOf(GoalWords.short(goal, names), GoalWords.detail(goal, names), GoalWords.met(goal, names))
}
