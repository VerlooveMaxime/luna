package game.idle.flow

import engine.combat.prayer.CombatPrayer
import game.content.sailing.ShipRoute
import game.idle.autopilot.firemaking.LightStepType
import game.idle.autopilot.fishing.FishingMethod
import game.idle.autopilot.making.LunaRecipes
import game.idle.autopilot.mining.MineStepType
import game.idle.autopilot.smithing.SmithStepType
import game.idle.autopilot.travel.LunaEntrances
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.flow.option.LunaGameNames
import game.idle.font.ClientFont
import game.idle.location.Tile
import game.idle.movement.TravelChoice
import game.idle.ui.BuilderWidgets
import game.obj.entrances.Entrance
import game.testworld.TestWorld
import io.luna.game.model.Position
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
        words = StepWords(LunaGameNames, plain, small, BuilderWidgets.LINE_ROOM)
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
            listOf(Reason.CannotMake, Reason.WrongWindow) +
            Place.entries.map { Reason.NoWay(it) } +
            Place.entries.flatMap { place -> TravelChoice.entries.map { Reason.NoWay(place, it) } } +
            Structure.entries.map { Reason.Impassable(it) } +
            entrances().map { Reason.Impassable(it) } +
            listOf(Reason.BagFull(emptyList()), Reason.NoneLeftInBank(longestNamedItem()), Reason.BankFull) +
            listOf(Reason.RunningClear(), Reason.RunningClear(toStep = 99))

    /**
     * Every cave and dungeon entrance of Luna's in the words a trip says them with, the form at value 0 of one that
     * changes form; one that leads the same way from everywhere is looked up from any tile.
     */
    private fun entrances(): List<EntrancePassage> = Entrance.entries.mapNotNull { LunaEntrances.passage(it.id, it.position ?: Position(0, 0)) }

    /** Every end, skip and stop a step can say, the bag-full one with two of the longest-named item left lying. */
    fun chatLines(): List<String> {
        val stuck = listOf(Reason.BagFull(emptyList()), Reason.BagFull(List(2) { longestNamedItem() }), Reason.NoneLeftInBank(longestNamedItem()), Reason.BankFull)
        return Skill.NAMES.indices.flatMap { listOf(words.stop(99, StepStop.Reflex(99, Fired.AtSkill(it, 99))), words.jump(99, Fired.AtSkill(it, 99), 99)) } + stuck.flatMap { listOf(words.said(99, Said.Ended(it)), words.said(99, Said.Skipped(it)), words.stop(99, StepStop.Stuck(it))) } +
            listOf(
                words.stop(99, StepStop.Reflex(99, Fired.AtHitpoints(Health(999, 999)))),
                words.stop(99, StepStop.Reflex(99, Fired.UnderAttack)),
                words.cutShort(99),
                words.jump(99, Fired.AtHitpoints(Health(999, 999)), 99),
                words.jump(99, Fired.UnderAttack, 99),
                words.stop(99, StepStop.Reflex(99, Fired.AtSpecialEnergy(100))),
                words.jump(99, Fired.AtSpecialEnergy(100), 99),
                words.usedSpecial(99),
                words.leftOut(99, Int.MAX_VALUE, longestNamedItem(), 99),
            ) + CombatPrayer.entries.flatMap { listOf(words.prayed(PrayerChange(99, it, on = true)), words.prayed(PrayerChange(99, it, on = false))) } +
            listOf(
                StepWords.cannotWork(99),
                StepWords.IDLE_LAP,
                StepWords.ALL_GOALS_MET,
                StepWords.NO_GOALS,
                words.stoppedAfterLaps(Int.MAX_VALUE),
                words.stoppedAfterMinutes(Int.MAX_VALUE),
            ) + goals().flatMap { listOf(words.goalMet(99, it), words.goalStops(99, it)) } + travelLines() + notThroughLines()
    }

    /** Every door a walk up can be stuck at, and every entrance of Luna's in the words the player sees, at the widest tile. */
    private fun notThroughLines(): List<String> =
        (listOf(Structure.DOOR, Structure.GATE, Structure.CURTAIN) + entrances()).map { words.said(99, Said.NotThrough(it, Tile(9999, 9999))) }

    /**
     * Every spend and long way a trip can tell (web-walker movement Part 3c): each teleport with all it lists, each piece
     * taken off, each piece not worn again.
     */
    private fun travelLines(): List<String> {
        val teleports = Teleports.all.flatMap { destination ->
            when (destination) {
                is TeleportDestination.Spell -> listOf(TeleportStarted.Cast(destination, Teleports.listed(destination.spell.requirements, worn = emptySet())))
                is TeleportDestination.Jewellery -> listOf(TeleportStarted.Rubbed(destination, tookOff = false), TeleportStarted.Rubbed(destination, tookOff = true))
            }
        }
        val jewellery = Teleports.all.filterIsInstance<TeleportDestination.Jewellery>()
        val reflexLines = jewellery.flatMap { listOf(ReflexSaid.TookOff(99, it), ReflexSaid.NotWornAgain(99, it)) }.map(words::reflexSaid)
        val notWornAgain = jewellery.map { words.said(99, Said.NotWornAgain(it)) }
        return teleports.map { words.said(99, Said.Teleported(it)) } + reflexLines + notWornAgain + words.said(99, Said.PaidToll(10)) +
            TravelChoice.entries.map { words.said(99, Said.LongWay(it)) } + ShipRoute.entries.map { words.said(99, Said.Fare(it.fare, it.place)) }
    }

    /** Every kind of goal with the longest words the data can put in it. */
    private fun goals(): List<Goal> {
        val longestSkill = Skill.NAMES.indices.maxBy { Skill.getName(it).length }
        return listOf(Goal.Level(longestSkill, 99), Goal.Count(Int.MAX_VALUE), Goal.Time(Int.MAX_VALUE), Goal.Bank(longestNamedItem(), emptySet(), Int.MAX_VALUE))
    }

    /** The tools and bait the kinds block on, the values their `needs()` use. */
    private fun tools(): List<ToolNeed> {
        val fishing = FishingMethod.ALL.flatMap { listOfNotNull(it.toolNeed, it.baitNeed) }
        val recipeTools = LunaRecipes(LunaGameNames).catalog().recipes.flatMap { recipe -> recipe.ways.flatMap { it.tools } }
        return listOf(ChopStepType.AXE, MineStepType.PICKAXE, LightStepType.TINDERBOX, SmithStepType.HAMMER) + fishing +
            recipeTools.distinct().map { ToolNeed(word = null, mapOf(it to 1)) }
    }

    private fun longestNamedItem(): Int = ItemDefinition.ALL.stream().filter { it.name != "null" }.toList().maxBy { it.name.length }.id()
}
