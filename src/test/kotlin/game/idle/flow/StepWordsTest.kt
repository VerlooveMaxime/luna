package game.idle.flow


import engine.combat.prayer.CombatPrayer
import game.idle.flow.option.FakeNames
import game.idle.font.EVEN_FONT
import game.idle.location.Tile
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
    fun `a walk up that could not get through a door says which and where`() {
        assertEquals("Autopilot: step 2 could not get through the gate at 3101, 3509.", words.said(2, Said.DoorShut(Passage.GATE, Tile(3101, 3509))))
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
        assertEquals("Autopilot: stopped, reflex 2 fired at 9/20 hitpoints.", words.stop(1, StepStop.Reflex(2, Fired.AtHitpoints(hurt))))
    }

    @Test
    fun `a reflex that fired under attack says so`() {
        assertEquals("Autopilot: stopped, reflex 2 fired under attack.", words.stop(1, StepStop.Reflex(2, Fired.UnderAttack)))
    }

    @Test
    fun `a step cut short again stops the flow by its number`() {
        assertEquals("Autopilot: stopped, step 1 was cut short again before it made progress.", words.cutShort(1))
    }

    @Test
    fun `a jump names the reflex, its hitpoints and the step it goes to`() {
        assertEquals("Autopilot: reflex 2 fired at 9/20 hitpoints, jumping to step 3.", words.jump(2, Fired.AtHitpoints(hurt), 3))
    }

    @Test
    fun `a bank step says what it leaves out and the step whose goal let it`() {
        assertEquals("Autopilot: step 3 leaves out 5 oak logs, step 1 met its goal.", words.leftOut(3, amount = 5, oakLogs, fed = 1))
    }

    @Test
    fun `a withdrawal of all left out names no amount`() {
        assertEquals("Autopilot: step 3 leaves out oak logs, step 1 met its goal.", words.leftOut(3, amount = null, oakLogs, fed = 1))
    }

    @Test
    fun `a step that cannot work points at its slot`() {
        assertEquals("Autopilot: step 4 cannot work yet. See its slot warning in the builder.", StepWords.cannotWork(4))
    }

    @Test
    fun `a reflex that cannot work points at its row`() {
        assertEquals("Autopilot: reflex 2 cannot work yet. See its row in the builder.", StepWords.reflexCannotWork(2))
    }

    @Test
    fun `the chat prefix is Autopilot and a space`() {
        assertEquals("Autopilot: ", StepWords.PREFIX)
    }

    @Test
    fun `death stops the flow in Maxime's words`() {
        assertEquals("Autopilot: stopped, you died.", StepWords.DIED)
    }

    @Test
    fun `an idle lap stops the flow with one line`() {
        assertEquals("Autopilot: stopped, no step had anything to do.", StepWords.IDLE_LAP)
    }

    @Test
    fun `a flow that no longer resolves points at the builder`() {
        assertEquals("Autopilot: could not resume your flow. Check it in the flow builder.", StepWords.CANNOT_RESUME)
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("underWayWords")
    fun `what a trip does on its way is said in a few words`(underWay: UnderWay, text: String) {
        assertEquals(text, words.underWay(underWay))
    }

    companion object {
        @JvmStatic
        fun underWayWords(): List<Arguments> = listOf(
            Arguments.of(UnderWay.Opening(Passage.DOOR), "Opening the door"),
            Arguments.of(UnderWay.Opening(Passage.GATE), "Opening the gate"),
            Arguments.of(UnderWay.Opening(Passage.TRAPDOOR), "Opening the trapdoor"),
            Arguments.of(UnderWay.Passing(Passage.DOOR), "Going through the door"),
            Arguments.of(UnderWay.Passing(Passage.CURTAIN), "Going through the curtain"),
            Arguments.of(UnderWay.Passing(Passage.LADDER_UP), "Climbing up the ladder"),
            Arguments.of(UnderWay.Passing(Passage.STAIRS_DOWN), "Climbing down the stairs"),
            Arguments.of(UnderWay.Passing(Passage.TRAPDOOR), "Climbing down the trapdoor"),
        )

        @JvmStatic
        fun fixedWords(): List<Arguments> = listOf(
            Arguments.of(Reason.NoneInReach(Target.TREE), "No tree in reach"),
            Arguments.of(Reason.NoneInReach(Target.ROCK), "No rock in reach"),
            Arguments.of(Reason.NoneInReach(Target.FISHING_SPOT), "No fishing spot in reach"),
            Arguments.of(Reason.NoneInReach(Target.FIRE_OR_RANGE), "No fire or range in reach"),
            Arguments.of(Reason.NoneInReach(Target.FURNACE), "No furnace in reach"),
            Arguments.of(Reason.NoneInReach(Target.ANVIL), "No anvil in reach"),
            Arguments.of(Reason.NoneInReach(Target.NPC), "Nothing to fight in reach"),
            Arguments.of(Reason.NoneInReach(Target.FREE_TILE), "No free tile for a fire"),
            Arguments.of(Reason.CannotMake, "Cannot make it yet"),
            Arguments.of(Reason.WrongWindow, "Another make window is open"),
            Arguments.of(Reason.NoWay(Place.TILE), "No way there from here"),
            Arguments.of(Reason.NoWay(Place.WORK_SPOT), "No way to the work spot from here"),
            Arguments.of(Reason.NoWay(Place.BANK), "No way to the bank from here"),
            Arguments.of(Reason.Impassable(Passage.DOOR), "No way through the door"),
            Arguments.of(Reason.Impassable(Passage.GATE), "No way through the gate"),
            Arguments.of(Reason.Impassable(Passage.CURTAIN), "No way through the curtain"),
            Arguments.of(Reason.Impassable(Passage.LADDER_UP), "No way up the ladder"),
            Arguments.of(Reason.Impassable(Passage.LADDER_DOWN), "No way down the ladder"),
            Arguments.of(Reason.Impassable(Passage.STAIRS_UP), "No way up the stairs"),
            Arguments.of(Reason.Impassable(Passage.STAIRS_DOWN), "No way down the stairs"),
            Arguments.of(Reason.Impassable(Passage.TRAPDOOR), "No way down the trapdoor"),
            Arguments.of(Reason.RunningClear(), "Running clear before stopping"),
            Arguments.of(Reason.RunningClear(toStep = 2), "Running clear to jump to step 2"),
            Arguments.of(Reason.BagFull(emptyList()), "The bag is full"),
            Arguments.of(Reason.BankFull, "The bank is full"),
        )
    }

    @Test
    fun `a step that met its goal says so and that it is skipped for the run`() {
        assertEquals("Autopilot: step 2 met its goal, Strength 20: skipped for this run.", words.goalMet(2, Goal.Level(Skill.STRENGTH, 20)))
    }

    @Test
    fun `a goal that stops the flow names the step and the goal`() {
        assertEquals("Autopilot: stopped, step 2 met its goal: 100 in all.", words.goalStops(2, Goal.Count(100)))
    }

    @Test
    fun `a bank goal names its item`() {
        assertEquals("Autopilot: step 2 met its goal, 100 oak logs in the bank: skipped for this run.", words.goalMet(2, Goal.Bank(oakLogs, setOf(oakLogs), 100)))
    }

    @Test
    fun `a bank goal's item name is cut so the line fits the chat box`() {
        val long = words(FakeNames(mapOf(oakLogs to "A".repeat(100))))

        val line = long.goalStops(2, Goal.Bank(oakLogs, setOf(oakLogs), 100))

        assertEquals("Autopilot: stopped, step 2 met its goal: 100 " + "a".repeat(31) + ".. in the bank.", line)
    }

    @Test
    fun `a time goal counts minutes`() {
        assertEquals("Autopilot: stopped, step 1 met its goal: 30 min.", words.goalStops(1, Goal.Time(30)))
    }

    @Test
    fun `the flow stops after its laps, one or more`() {
        assertEquals(listOf("Autopilot: stopped after 1 lap.", "Autopilot: stopped after 5 laps."), listOf(words.stoppedAfterLaps(1), words.stoppedAfterLaps(5)))
    }

    @Test
    fun `the flow stops after its minutes, one or more`() {
        assertEquals(listOf("Autopilot: stopped after 1 minute.", "Autopilot: stopped after 30 minutes."), listOf(words.stoppedAfterMinutes(1), words.stoppedAfterMinutes(30)))
    }

    @Test
    fun `every goal met stops the flow with one line`() {
        assertEquals("Autopilot: stopped, every goal is met.", StepWords.ALL_GOALS_MET)
    }

    @Test
    fun `a flow stopping on every goal met needs a goal`() {
        assertEquals("Autopilot: no step has an Until goal.", StepWords.NO_GOALS)
    }

    @Test
    fun `a reflex that lit a prayer says so by its number and the prayer's name`() {
        assertEquals("Autopilot: reflex 1 prays Protect from melee.", words.prayed(PrayerChange(1, CombatPrayer.PROTECT_FROM_MELEE, on = true)))
    }

    @Test
    fun `a reflex that put out a prayer says so`() {
        assertEquals("Autopilot: reflex 1 stops praying Protect from melee.", words.prayed(PrayerChange(1, CombatPrayer.PROTECT_FROM_MELEE, on = false)))
    }

    @Test
    fun `a reflex that lit the special attack bar says it used it`() {
        assertEquals("Autopilot: reflex 3 used the special attack.", words.usedSpecial(3))
    }

    @Test
    fun `a special energy reflex that stops the flow says the energy it fired at`() {
        assertEquals("Autopilot: stopped, reflex 2 fired at 70% special energy.", words.stop(1, StepStop.Reflex(2, Fired.AtSpecialEnergy(70))))
    }

    @Test
    fun `a special energy reflex that jumps says the energy it fired at`() {
        assertEquals("Autopilot: reflex 2 fired at 70% special energy, jumping to step 3.", words.jump(2, Fired.AtSpecialEnergy(70), 3))
    }

    @Test
    fun `a skill reflex says the current skill level when it stops or jumps`() {
        val fired = Fired.AtSkill(Skill.STRENGTH, 24)

        assertEquals("Autopilot: stopped, reflex 2 fired at Strength 24.", words.stop(1, StepStop.Reflex(2, fired)))
        assertEquals("Autopilot: reflex 2 fired at Strength 24, jumping to step 3.", words.jump(2, fired, 3))
    }

}
