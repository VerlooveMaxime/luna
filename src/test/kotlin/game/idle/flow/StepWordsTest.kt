package game.idle.flow

import game.idle.flow.option.FakeNames
import game.idle.ui.EVEN_FONT
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/** The words themselves; that they fit the slot and the chat box in the client's fonts is `StepWordsFitTest`. */
class StepWordsTest {

    private val bones = 526
    private val cowhide = 1739
    private val oakLogs = 1521
    private val net = 303
    private val bait = 313
    private val bonesNoted = 527
    private val words = words(FakeNames(mapOf(bones to "Bones", bonesNoted to "Bones", cowhide to "Cowhide", oakLogs to "Oak logs", net to "Small fishing net", bait to "Fishing bait")))
    private val axe = ToolNeed("axe", mapOf(1351 to 1, 1349 to 1), Skill.WOODCUTTING)
    private val hurt = Health(9, 20)

    @Test
    fun `a tool with levels needs one the player can use`() {
        assertEquals("Needs an axe you can use", words.block(Reason.NeedsTool(axe)))
    }

    @Test
    fun `the article follows the tool's word`() {
        assertEquals("Needs a pickaxe you can use", words.block(Reason.NeedsTool(ToolNeed("pickaxe", mapOf(1265 to 1), Skill.MINING))))
    }

    @Test
    fun `a tool without a word is named after its item`() {
        assertEquals("Needs a small fishing net", words.block(Reason.NeedsTool(ToolNeed(word = null, mapOf(net to 1)))))
    }

    @Test
    fun `bait is needed as is, not one of it`() {
        assertEquals("Needs fishing bait", words.block(Reason.NeedsTool(ToolNeed(word = null, mapOf(bait to 1), countable = false))))
    }

    @Test
    fun `a level names the skill`() {
        assertEquals("Needs Mining 15", words.block(Reason.NeedsLevel(Skill.MINING, 15)))
    }

    @Test
    fun `an item left in the bank is named`() {
        assertEquals("No oak logs left in the bank", words.block(Reason.NoneLeftInBank(oakLogs)))
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("fixedWords")
    fun `a reason has one wording`(reason: Reason, expected: String) {
        assertEquals(expected, words.block(reason))
    }

    @Test
    fun `a step that ended on a full bag says what it left lying`() {
        assertEquals(
            "Autopilot: step 2 ended, the bag is full: bones, cowhide left lying.",
            words.said(2, Said.Ended(Reason.BagFull(listOf(bones, cowhide)))),
        )
    }

    @Test
    fun `a step that ended on a full bag with nothing left says only that`() {
        assertEquals("Autopilot: step 2 ended, the bag is full.", words.said(2, Said.Ended(Reason.BagFull(emptyList()))))
    }

    @Test
    fun `items left lying of one name are named once`() {
        assertEquals("Autopilot: step 2 ended, the bag is full: bones left lying.", words.said(2, Said.Ended(Reason.BagFull(listOf(bones, bonesNoted)))))
    }

    @Test
    fun `what was left lying is cut to the chat box`() {
        val line = words.said(2, Said.Ended(Reason.BagFull(listOf(bones, cowhide, oakLogs, net, bait, bones, cowhide, oakLogs))))

        assertTrue(EVEN_FONT.width(line) <= StepWords.CHAT_ROOM, line)
    }

    @Test
    fun `a cut list still ends the line as it should`() {
        val line = words.said(2, Said.Ended(Reason.BagFull(listOf(bones, cowhide, oakLogs, net, bait, bones, cowhide, oakLogs))))

        assertTrue(line.endsWith(".. left lying."), line)
    }

    @Test
    fun `a skipped step says why in the same words as its block, in lower case`() {
        assertEquals("Autopilot: step 3 skipped this lap: no oak logs left in the bank.", words.said(3, Said.Skipped(Reason.NoneLeftInBank(oakLogs))))
    }

    @Test
    fun `a stuck step that stops the flow says which step and why`() {
        assertEquals("Autopilot: stopped, step 3 is stuck: the bank is full.", words.stop(3, StepStop.Stuck(Reason.BankFull)))
    }

    @Test
    fun `a reflex that stops the flow says which reflex and at what hitpoints`() {
        assertEquals("Autopilot: stopped, reflex 2 ran at 9/20 hitpoints.", words.stop(1, StepStop.Reflex(2, hurt)))
    }

    @Test
    fun `a step cut short again stops the flow by its number`() {
        assertEquals("Autopilot: stopped, step 1 was cut short again before it made progress.", words.cutShort(1))
    }

    @Test
    fun `a jump names the reflex, its hitpoints and the step it goes to`() {
        assertEquals("Autopilot: reflex 2 ran at 9/20 hitpoints, jumping to step 3.", words.jump(2, hurt, 3))
    }

    @Test
    fun `a step that cannot work points at its slot`() {
        assertEquals("Autopilot: step 4 cannot work yet. See its slot warning in the builder.", StepWords.cannotWork(4))
    }

    @Test
    fun `death stops the flow in Maxime's words`() {
        assertEquals("Autopilot: stopped, you died.", StepWords.DIED)
    }

    @Test
    fun `a flow that no longer resolves points at the builder`() {
        assertEquals("Autopilot: could not resume your flow. Check it in the flow builder.", StepWords.CANNOT_RESUME)
    }

    companion object {
        @JvmStatic
        fun fixedWords(): List<Arguments> = listOf(
            Arguments.of(Reason.NoneInReach(Target.TREE), "No tree in reach"),
            Arguments.of(Reason.NoneInReach(Target.ROCK), "No rock in reach"),
            Arguments.of(Reason.NoneInReach(Target.FISHING_SPOT), "No fishing spot in reach"),
            Arguments.of(Reason.NoneInReach(Target.FIRE_OR_RANGE), "No fire or range in reach"),
            Arguments.of(Reason.NoneInReach(Target.FURNACE), "No furnace in reach"),
            Arguments.of(Reason.NoneInReach(Target.ANVIL), "No anvil in reach"),
            Arguments.of(Reason.NoneInReach(Target.NPC), "Nothing to fight in reach"),
            Arguments.of(Reason.NoneInReach(Target.BANK_BOOTH), "No bank booth on this floor"),
            Arguments.of(Reason.NoneInReach(Target.FREE_TILE), "No free tile for a fire"),
            Arguments.of(Reason.NothingToUse(Supply.LOGS), "No logs in the bag"),
            Arguments.of(Reason.NothingToUse(Supply.RAW_FOOD), "No raw food in the bag"),
            Arguments.of(Reason.NothingToUse(Supply.INGREDIENTS), "An ingredient is missing"),
            Arguments.of(Reason.NothingToUse(Supply.ORE), "Not enough ore in the bag"),
            Arguments.of(Reason.NothingToUse(Supply.BARS), "Not enough bars in the bag"),
            Arguments.of(Reason.CannotMake, "Cannot make it yet"),
            Arguments.of(Reason.WrongWindow, "Another make window is open"),
            Arguments.of(Reason.OtherFloor(Place.TILE), "Cannot walk to another floor"),
            Arguments.of(Reason.OtherFloor(Place.WORK_SPOT), "The work spot is on another floor"),
            Arguments.of(Reason.NoWay(Place.TILE), "No way there from here"),
            Arguments.of(Reason.NoWay(Place.WORK_SPOT), "No way to the work spot from here"),
            Arguments.of(Reason.BagFull(emptyList()), "The bag is full"),
            Arguments.of(Reason.BankFull, "The bank is full"),
        )
    }
}
