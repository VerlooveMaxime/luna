package game.idle.flow

import game.idle.autopilot.firemaking.LightPlanner
import game.idle.autopilot.fishing.FishingMethod
import game.idle.autopilot.fishing.FishingNeeds
import game.idle.autopilot.making.LunaRecipes
import game.idle.autopilot.mining.MiningPlanner
import game.idle.autopilot.smithing.SmithingPlanner
import game.idle.autopilot.woodcutting.WoodcuttingPlanner
import game.idle.flow.option.LunaGameNames
import game.idle.ui.BuilderWidgets
import game.idle.ui.ClientFont
import game.testworld.TestWorld
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Every reason, with the longest real names the data holds, fits where the client shows it: a block on two of the
 * slot's lines in the small font (Maxime, 2026-10-11), a chat line in the chat box's width in the plain font. Measured
 * with the fonts of the cache, so these run on [TestWorld].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StepWordsFitTest {

    private lateinit var small: ClientFont
    private lateinit var plain: ClientFont
    private lateinit var words: StepWords

    @BeforeAll
    fun load() {
        val cache = TestWorld.context.cache
        small = ClientFont.fromCache(cache)
        plain = ClientFont.fromCache(cache, ClientFont.PLAIN)
        words = StepWords(LunaGameNames, plain)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reasons")
    fun `a block fits two slot lines`(reason: Reason) {
        val block = "! ${words.block(reason)}"

        val lines = small.wrap(block, BuilderWidgets.LINE_ROOM, BuilderWidgets.SLOT_LINES)

        assertTrue(lines.size <= StepWords.SLOT_LINES, "$block takes ${lines.size} lines")
        assertEquals(block, lines.joinToString(" "))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chatLines")
    fun `a chat line fits the chat box`(line: String) {
        assertTrue(plain.width(line) <= StepWords.CHAT_ROOM, "$line is ${plain.width(line)} px")
    }

    /** Every variant, the ones naming an item or a skill once per name the data can put there. */
    fun reasons(): List<Reason> =
        tools().map { Reason.NeedsTool(it) } +
            Skill.NAMES.indices.map { Reason.NeedsLevel(it, 99) } +
            Target.entries.map { Reason.NoneInReach(it) } +
            Supply.entries.map { Reason.NothingToUse(it) } +
            listOf(Reason.CannotMake, Reason.WrongWindow) +
            Place.entries.flatMap { listOf(Reason.OtherFloor(it), Reason.NoWay(it)) } +
            listOf(Reason.BagFull(emptyList()), Reason.NoneLeftInBank(longestNamedItem()), Reason.BankFull)

    /** Every end, skip and stop a step can say, the bag-full one with two of the longest-named item left lying. */
    fun chatLines(): List<String> {
        val stuck = listOf(Reason.BagFull(emptyList()), Reason.BagFull(List(2) { longestNamedItem() }), Reason.NoneLeftInBank(longestNamedItem()), Reason.BankFull)
        return stuck.flatMap { listOf(words.said(99, Said.Ended(it)), words.said(99, Said.Skipped(it)), words.stop(99, StepStop.Stuck(it))) } +
            listOf(
                words.stop(99, StepStop.Reflex(99, Health(999, 999))),
                words.cutShort(99),
                words.jump(99, Health(999, 999), 99),
                StepWords.cannotWork(99),
            )
    }

    /** The tools and bait the kinds block on, the same values their planners and `needs()` use. */
    private fun tools(): List<ToolNeed> {
        val fishing = FishingMethod.ALL.map(FishingNeeds::of).flatMap { listOfNotNull(it.tool, it.bait) }
        val recipeTools = LunaRecipes(LunaGameNames).catalog().recipes.flatMap { recipe -> recipe.ways.flatMap { it.tools } }
        return listOf(WoodcuttingPlanner.AXE, MiningPlanner.PICKAXE, LightPlanner.TINDERBOX, SmithingPlanner.HAMMER) + fishing +
            recipeTools.distinct().map { ToolNeed(word = null, mapOf(it to 1)) }
    }

    private fun longestNamedItem(): Int = ItemDefinition.ALL.stream().filter { it.name != "null" }.toList().maxBy { it.name.length }.id()
}
