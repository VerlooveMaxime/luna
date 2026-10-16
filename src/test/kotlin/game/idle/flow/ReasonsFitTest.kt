package game.idle.flow

import game.idle.autopilot.IdleSteps
import game.idle.autopilot.reflex.FoodOptions
import game.idle.autopilot.reflex.PotionOptions
import game.idle.autopilot.reflex.PrayerOptions
import game.idle.autopilot.reflex.SpecialOptions
import game.idle.autopilot.reflex.TeleportOptions
import game.idle.danger.DangerCatalog
import game.idle.danger.LunaDangers
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.flow.option.OptionFacts
import game.idle.font.ClientFont
import game.idle.location.Tile
import game.idle.ui.BuilderWidgets
import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.combat.SpecialAttackType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every reason the builder can show for a step, over the real kinds and data, takes the decided form (Maxime,
 * 2026-10-11, Q13: sentence case, no period, no "Autopilot:", the bag never "inventory") and fits two of the slot's
 * lines in the client's small font, as the run-time reasons do (`StepWordsFitTest`); the warnings take the form, and
 * every kind's description fits the configure screen's header. Measured with the fonts of the cache, so these run on
 * [TestWorld]. Every danger line of Luna's spawns takes the form too, and its slot form fits the slot (S08).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReasonsFitTest {

    private lateinit var small: ClientFont
    private lateinit var steps: IdleSteps
    private lateinit var resolver: FlowResolver
    private lateinit var reflexes: ReflexResolver
    private lateinit var dangers: DangerCatalog
    private lateinit var warnings: StepWarnings
    private lateinit var form: ReflexForm

    @BeforeAll
    fun load() {
        small = ClientFont.fromCache(TestWorld.context.cache)
        steps = IdleSteps.load()
        resolver = FlowResolver(steps.types)
        reflexes = ReflexResolver(FoodOptions.portions(), PotionOptions.doses())
        dangers = LunaDangers(LunaGameNames).catalog()
        warnings = StepWarnings(resolver, LunaGameNames, StepDangers(resolver, dangers, LunaGameNames))
        form = ReflexForm(FoodOptions(LunaGameNames), FoodOptions.portions(), steps.types, LunaGameNames, PotionOptions(LunaGameNames), PotionOptions.doses(), PrayerOptions, SpecialOptions::drains, TeleportOptions(LunaGameNames))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSteps")
    fun `a step's reason takes the form`(name: String, flow: List<StepSettings>) {
        assertInForm(reasonOf(name, flow))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenSteps")
    fun `a step's reason fits two slot lines`(name: String, flow: List<StepSettings>) {
        assertFitsSlot(reasonOf(name, flow))
    }

    @Test
    fun `an input no step before gets fits with the longest item name`() {
        val refusal = assertThrows<FlowError> { ProcessInput.requireGathered(InputSource.EARLIER_STEPS, FlowContext(), listOf(longestNamedItem()), LunaGameNames) }

        assertFitsSlot(refusal.message)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenReflexes")
    fun `a reflex's reason takes the form`(name: String, reflex: ReflexSettings) {
        val reason = requireNotNull(reflexes.problems(listOf(walk), listOf(reflex)).single()) { "$name resolves" }

        assertInForm(reason)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("warned")
    fun `a warning takes the form`(name: String, warning: String) {
        assertInForm(warning)
    }

    @Test
    fun `every warning the kinds and reflexes can give is measured`() {
        assertEquals(35, warned().size)
    }

    @Test
    fun `every toggle word of the reflex screen fits its button in the small font`() {
        val toggles = form.fields(emptyList()).filterIsInstance<StepField.Toggle>()

        val tooWide = toggles.flatMap { toggle -> toggle.choices.map { it.word to BuilderWidgets.toggleWidth(toggle.choices.size) } }
            .filter { (word, width) -> small.width(word) > width - toggleMargin }

        assertEquals(emptyList<Pair<String, Int>>(), tooWide)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("threats")
    fun `a danger's band line takes the form`(name: String, line: String, short: String) {
        assertInForm(line)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("threats")
    fun `a danger's slot line takes the form and fits the lines a slot holds for it`(name: String, line: String, short: String) {
        assertInForm(short)
        assertFitsSlot(short, lines = BuilderWidgets.SLOT_LINES - 1)
    }

    @Test
    fun `every aggressive npc of the world is measured`() {
        assertTrue(threats().size > 100, "${threats().size} kinds of aggressive npc")
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("descriptions")
    fun `a description fits the configure screen's header`(name: String, description: String) {
        assertTrue(small.width(description) <= BuilderWidgets.DESCRIPTION_ROOM, "$description is ${small.width(description)} px")
    }

    private fun reasonOf(name: String, flow: List<StepSettings>): String =
        requireNotNull(resolver.problems(flow).last()) { "$name resolves" }

    /** Sentence case (a quoted value may lead), no kind word first, no period, no prefix, no "inventory". */
    private fun assertInForm(text: String) {
        assertTrue(text.first().isUpperCase() || text.first() == '\'', "'$text' starts in sentence case")
        assertFalse(steps.types.all.any { text.lowercase().startsWith("${it.label} ") }, "'$text' names no kind")
        assertFalse(text.endsWith("."), "'$text' ends with no period")
        assertFalse("Autopilot" in text || "inventory" in text, "'$text' is a fragment")
    }

    private fun assertFitsSlot(reason: String, lines: Int = StepWords.SLOT_LINES) {
        val held = "! $reason"
        val wrapped = small.wrap(held, BuilderWidgets.LINE_ROOM, lines)
        assertEquals(held, wrapped.joinToString(" "), "'$held' takes more than $lines lines")
    }

    private val walk = StepSettings("walk", mapOf("tile" to "3101 3509"))

    /** A toggle's word is centred on its button: this much of the button stays clear around it. */
    private val toggleMargin = 4

    private fun step(kind: String, vararg values: Pair<String, String>) = StepSettings(kind, mapOf(*values))

    /** The flows whose last step cannot work, one per reason a kind can give, with the values the data makes longest. */
    fun brokenSteps(): List<Arguments> = listOf(
        "chop with no tree" to listOf(step("chop")),
        "chop of a tree that is not one" to listOf(step("chop", "tree" to "palm")),
        "chop of a tree the world has none of" to listOf(step("chop", "tree" to "mahogany")),
        "chop with a count of nothing" to listOf(step("chop", "tree" to "normal", "amount" to "0")),
        "chop too far around" to listOf(step("chop", "tree" to "normal", "within" to "33")),
        "chop at a spot that is no tile" to listOf(step("chop", "tree" to "normal", "spot" to "3101")),
        "mine with no rock" to listOf(step("mine")),
        "mine of a rock that is not one" to listOf(step("mine", "ore" to "rune_essence")),
        "fish with no catch" to listOf(step("fish")),
        "fish of a catch that is not one" to listOf(step("fish", "fish" to "karambwan")),
        "light with no logs" to listOf(step("light", "input" to "bank")),
        "light of logs no step before gets" to listOf(step("light", "input" to "earlier", "logs" to "1521")),
        "cook with no raw food" to listOf(step("cook", "input" to "bank")),
        "make with no product" to listOf(step("make")),
        "make of a product that is not one" to listOf(step("make", "product" to "cake")),
        "smelt with no bar" to listOf(step("smelt")),
        "smelt of a bar that is not one" to listOf(step("smelt", "bar" to "tin")),
        "smelt of ores no step before gets" to listOf(step("smelt", "bar" to "bronze", "input" to "earlier")),
        "smith with no item" to listOf(step("smith")),
        "smith of an item that is not one" to listOf(step("smith", "item" to "dagger")),
        "fight with no npc" to listOf(step("fight")),
        "fight of an npc that is not one" to listOf(step("fight", "npc" to "nobody")),
        "fight with a count of nothing" to listOf(step("fight", "npc" to "man", "amount" to "0")),
        "pick up with no items" to listOf(step("pickup")),
        "rest past full" to listOf(step("rest", "hitpoints" to "101")),
        "walk with no tile" to listOf(step("walk")),
        "walk to no tile" to listOf(step("walk", "tile" to "3101")),
        "drop with no step that gathers" to listOf(step("drop")),
        "drop with no chosen items" to listOf(step("drop", "drop" to "chosen")),
        "bank at a bank that is not one" to listOf(step("bank", "bank" to "lumbridge")),
        "a kind that is not one" to listOf(step("dig")),
        "a goal of a kind that is none" to listOf(step("chop", "tree" to "normal", "goal" to "xp")),
        "a level goal with no level" to listOf(step("chop", "tree" to "normal", "goal" to "level")),
        "a level goal past 99" to listOf(step("chop", "tree" to "normal", "goal" to "level", "goalLevel" to "100")),
        "a level goal in a skill that is none" to listOf(step("chop", "tree" to "normal", "goal" to "level", "goalSkill" to "flying", "goalLevel" to "45")),
        "a level goal on a kind without a skill" to listOf(step("pickup", "items" to "526", "goal" to "level", "goalLevel" to "45")),
        "a count goal with no count" to listOf(step("chop", "tree" to "normal", "goal" to "count")),
        "a count goal of nothing" to listOf(step("chop", "tree" to "normal", "goal" to "count", "goalCount" to "0")),
        "a time goal with no minutes" to listOf(step("chop", "tree" to "normal", "goal" to "time")),
        "a time goal of no minutes" to listOf(step("chop", "tree" to "normal", "goal" to "time", "goalMinutes" to "0")),
        "a bank goal with no item" to listOf(step("chop", "tree" to "normal", "goal" to "bank", "goalCount" to "1")),
        "a bank goal of an item that is none" to listOf(step("chop", "tree" to "normal", "goal" to "bank", "goalItem" to "nothing", "goalCount" to "1")),
        "a bank goal with no count" to listOf(step("chop", "tree" to "normal", "goal" to "bank", "goalItem" to "1521")),
        "a goal met by what is no choice" to listOf(step("chop", "tree" to "normal", "goal" to "count", "goalCount" to "1", "goalMet" to "pause")),
    ).map { (name, flow) -> Arguments.of(name, flow) }

    fun brokenReflexes(): List<Arguments> = listOf(
        "a skill reflex without a skill" to ReflexSettings(1, mapOf("when" to "skill")),
        "a skill reflex without a level" to ReflexSettings(1, mapOf("when" to "skill", "skillName" to "strength")),
        "a skill reflex with a bad level" to ReflexSettings(1, mapOf("when" to "skill", "skillName" to "strength", "skillBelow" to "100")),
        "a skill reflex with an unknown skill" to ReflexSettings(1, mapOf("when" to "skill", "skillName" to "flying")),
        "a skill reflex with hitpoints" to ReflexSettings(1, mapOf("when" to "skill", "skillName" to "hitpoints")),
        "a drink reflex without a potion" to ReflexSettings(1, mapOf("do" to "drink")),
        "a drink reflex with no such potion" to ReflexSettings(1, mapOf("do" to "drink", "potions" to "1511")),
        "a reflex below nothing" to ReflexSettings(1, mapOf("below" to "0")),
        "a pray reflex without a prayer" to ReflexSettings(1, mapOf("do" to "pray")),
        "a pray reflex with no such prayer" to ReflexSettings(1, mapOf("do" to "pray", "prayers" to "1511")),
        "a pray reflex with two of one group, the longest named" to ReflexSettings(1, mapOf("do" to "pray", "prayers" to "5622,5621")),
        "a reflex doing what no reflex does" to ReflexSettings(1, mapOf("do" to "dance")),
        "a teleport reflex without a destination" to ReflexSettings(1, mapOf("do" to "teleport")),
        "a teleport reflex to a place no teleport offers, the longest wilderness spell" to ReflexSettings(1, mapOf("do" to "teleport", "destination" to "carrallanger")),
        "a teleport reflex with a Then that is no choice" to ReflexSettings(1, mapOf("do" to "teleport", "destination" to "varrock", "then" to "elsewhere")),
        "a teleport reflex whose Then jumps to no step" to ReflexSettings(1, mapOf("do" to "teleport", "destination" to "varrock", "then" to "jump")),
        "a reflex eating what is not food" to ReflexSettings(1, mapOf("foods" to "1511")),
        "a reflex firing on what no reflex watches" to ReflexSettings(1, mapOf("when" to "poisoned")),
        "a special reflex with a count of nothing" to ReflexSettings(1, mapOf("when" to "special", "specialFor" to "0")),
        "a special reflex with a count past the bar" to ReflexSettings(1, mapOf("when" to "special", "specialFor" to "11")),
        "a reflex jumping to no step" to ReflexSettings(1, mapOf("do" to "jump")),
        "a reflex jumping to a deleted step" to ReflexSettings(1, mapOf("do" to "jump", "step" to "99")),
    ).map { (name, reflex) -> Arguments.of(name, reflex) }

    /** Every warning the configure screen can show, from flows that earn each one. */
    fun warned(): List<Arguments> {
        val chop = step("chop", "tree" to "normal")
        val flows = listOf(
            listOf(chop) to 0,
            listOf(step("fish", "fish" to "trout")) to 0,
            listOf(step("fight", "npc" to "man")) to 0,
            listOf(step("light", "input" to "bank", "logs" to "1511")) to 0,
            listOf(step("pickup", "items" to "526")) to 0,
            listOf(chop, step("bank", "deposit" to "everything")) to 0,
            listOf(step("chop", "tree" to "willow", "spot" to DRAYNOR_WILLOWS.text())) to 0,
        )
        val facts = OptionFacts(bag = setOf(BRONZE_AXE))
        val eatAnyFood = form.needs(ReflexSettings(1), facts).single()
        val drink = form.needs(ReflexSettings(1, mapOf("do" to "drink", "potions" to "113")), facts).single()
        return listOfNotNull(warnings.supplies(listOf(chop), drink, facts)).map { Arguments.of("reflex: $it", it) } +
            listOf(ReflexSettings(1, mapOf("when" to "attacked", "do" to "drink")), ReflexSettings(1, mapOf("when" to "skill")), ReflexSettings(1, mapOf("when" to "skill", "skillName" to "prayer", "do" to "pray")))
                .flatMap { form.warnings(it, facts) }.map { Arguments.of("reflex: $it", it) } + flows.flatMap { (flow, index) ->
            warnings.of(flow, index, facts, emptyList()).map { Arguments.of("${flow[index].kind}: $it", it) }
        } + (listOfNotNull(warnings.supplies(listOf(chop), eatAnyFood, facts)) + form.warnings(ReflexSettings(1, mapOf("when" to "attacked")), facts))
            .map { Arguments.of("reflex: $it", it) } + (specialWarnings() + teleportWarnings()).map { Arguments.of("reflex: $it", it) }
    }

    /**
     * The teleports' warnings: every rune and item a spell needs and every jewellery, from the flow that carries none,
     * and each spellbook's warning with its longest-named spell.
     */
    private fun teleportWarnings(): List<String> {
        val chop = step("chop", "tree" to "normal")
        val supplies = Teleports.all.map { ReflexSettings(1, mapOf("do" to "teleport", "destination" to Teleports.key(it))) }
            .flatMap { form.needs(it, OptionFacts()) }.distinct().mapNotNull { warnings.supplies(listOf(chop), it, OptionFacts()) }
        val spells = Teleports.all.filterIsInstance<TeleportDestination.Spell>()
        val books = Spellbook.entries.map { book -> spells.filter { it.spell.style.spellbook != book }.maxBy { Teleports.named(it).length } to book }
        return supplies + books.flatMap { (spell, book) -> form.warnings(ReflexSettings(1, mapOf("do" to "teleport", "destination" to Teleports.key(spell))), OptionFacts(spellbook = book)) }
    }

    /**
     * The special attack's three warnings: no weapon worn, and a count the bar cannot hold with the longest-named
     * weapon that has a special, one where one special fits the bar and one where several do.
     */
    private fun specialWarnings(): List<String> {
        val weapon = SpecialAttackType.entries.flatMap { it.ids }.maxBy { LunaGameNames.item(it).length }
        val twoSpecials = ReflexSettings(1, mapOf("when" to "special", "specialFor" to "2"))
        val tenSpecials = ReflexSettings(1, mapOf("when" to "special", "specialFor" to "10"))
        val worn = OptionFacts(worn = setOf(weapon))
        return form.warnings(ReflexSettings(1, mapOf("do" to "special")), OptionFacts()) +
            longestNamedForm { mapOf(weapon to 100) }.warnings(twoSpecials, worn) +
            longestNamedForm { mapOf(weapon to 34) }.warnings(tenSpecials, worn)
    }

    private fun longestNamedForm(specials: () -> Map<Int, Int>) =
        ReflexForm(FoodOptions(LunaGameNames), FoodOptions.portions(), steps.types, LunaGameNames, PotionOptions(LunaGameNames), PotionOptions.doses(), PrayerOptions, specials, TeleportOptions(LunaGameNames))

    /** Every kind of aggressive npc Luna spawns: its band line and its slot line. */
    fun threats(): List<Arguments> = dangers.all.map { Arguments.of("${it.name} ${it.level}", it.line, it.short) }

    fun descriptions(): List<Arguments> =
        steps.types.all.map { Arguments.of(it.kind, it.description) } +
            listOf("eat", "drink", "pray", "special", "teleport", "stop", "jump").map { ReflexSettings(1, mapOf("do" to it)) }.map { Arguments.of(form.label(it), form.description(it)) }

    private fun longestNamedItem(): Int = ItemDefinition.ALL.stream().filter { it.name != "null" }.toList().maxBy { it.name.length }.id()

    private companion object {
        const val BRONZE_AXE = 1351

        /** Within reach of the two dark wizards and, as Luna's data has it, a Black Knight (S08). */
        val DRAYNOR_WILLOWS = Tile(3087, 3229)
    }
}
