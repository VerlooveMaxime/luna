package game.idle.flow

import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.cooking.CookStep
import game.idle.autopilot.cooking.CookingWork
import game.idle.autopilot.cooking.CookingView
import game.idle.autopilot.cooking.FakeCooker
import game.idle.autopilot.firemaking.FakeLighter
import game.idle.autopilot.firemaking.LightStep
import game.idle.autopilot.firemaking.LightView
import game.idle.autopilot.firemaking.LightWork
import game.idle.autopilot.making.FakeMaker
import game.idle.autopilot.making.MakeStep
import game.idle.autopilot.making.MakeView
import game.idle.autopilot.making.MakeWork
import game.idle.autopilot.making.Recipe
import game.idle.autopilot.making.RecipeWay
import game.idle.autopilot.smelting.FakeSmelter
import game.idle.autopilot.smelting.SmeltStep
import game.idle.autopilot.smelting.SmeltingWork
import game.idle.autopilot.smelting.SmeltingView
import game.idle.autopilot.smithing.FakeSmither
import game.idle.autopilot.smithing.SmithStep
import game.idle.autopilot.smithing.SmithingWork
import game.idle.autopilot.smithing.SmithingView
import game.idle.autopilot.walk.FakeWalker
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill

/** The kinds that work on input from the bag: light, cook, make, smelt, smith. */
fun processKinds(): List<ProcessFixture> = listOf(LightFixture(), CookFixture(), MakeFixture(), SmeltFixture(), SmithFixture())

private const val TINDERBOX = 590
private const val OAK_LOGS = 1521
private const val RAW_TROUT = 335
private const val KNIFE = 946
private const val LOGS = 1511
private const val SHORTBOW_U = 50
private const val IRON_ORE = 440
private const val HAMMER = 2347
private const val IRON_BAR = 2351
private const val FIRE = 2732
private const val FURNACE = 2781
private const val ANVIL = 2783

/** Where a kind that works where it stands has its turn built; its walk back is never used. */
private val HERE = Tile(3200, 3200)

/**
 * Light with a tinderbox and five oak logs where the player stands, the tile free. Its count is the logs used, so the
 * meter falls: [gain] takes logs out of the bag. The fire catching is the lighter busy, as its work's `finishing()` reads.
 */
class LightFixture : ProcessFixture, ToolFixture, SettlingFixture {

    override val name = "light"

    override val tool = ToolNeed(word = null, mapOf(TINDERBOX to 1))

    override val level = LevelNeed(Skill.FIREMAKING, 15)

    override val carried = FakeCarried().apply {
        bag += setOf(TINDERBOX, OAK_LOGS)
        levels[level.skill] = level.level
    }

    private val step = LightStep(setOf(OAK_LOGS))
    private val lighter = FakeLighter(LightView(lightable = 0, tileFree = true)).apply { logs = 5 }

    override val done: List<String> get() = lighter.steps

    override fun turn(amount: Int?): StepTurn =
        turnOf(LightWork(lighter), amount, step.needs(), carried, fillsBag = false, FakeWalker(HERE))

    override fun gain(units: Int) {
        lighter.logs -= units
    }

    override fun adapterBusy(busy: Boolean) {
        lighter.busy = busy
    }

    override fun settling(settling: Boolean) {
        lighter.busy = settling
    }

    override fun emptyInput() {
        carried.bag -= OAK_LOGS
        lighter.logs = 0
        lighter.view = lighter.view.copy(lightable = null)
    }

    override fun supply(units: Int) {
        carried.bag += OAK_LOGS
        lighter.logs += units
        lighter.view = lighter.view.copy(lightable = 0)
    }

    override fun toString(): String = name
}

/** Cook with five raw trout, a fire in reach. Its count is the raw food used, so the meter falls. */
class CookFixture : SpotFixture("cook", LevelNeed(Skill.COOKING, 15), bag = setOf(RAW_TROUT)), ProcessFixture {

    /** Luna's food names itself from the item definitions, which need the cache. */
    init {
        TestWorld.world
    }

    private val step = CookStep(setOf(RAW_TROUT), RADIUS, WorkSpot.At(anchor))
    private val fire = PlaceCandidate(FIRE, targetTile, distance = 0, usableFromHere = true, anchor.toPosition())
    private val cooker = FakeCooker(CookingView(rawSlot = 0, windowOpen = false, atLocation = true, listOf(fire)), anchor).apply { raw = 5 }

    override val done: List<String> get() = cooker.steps

    override fun turn(amount: Int?): StepTurn = turnOf(CookingWork(cooker), amount, step.needs(), carried, fillsBag = false, walker)

    override fun gain(units: Int) {
        cooker.raw -= units
    }

    override fun adapterBusy(busy: Boolean) {
        cooker.busy = busy
    }

    override fun clearTargets() {
        cooker.view = cooker.view.copy(places = emptyList())
    }

    override fun restoreTargets() {
        cooker.view = cooker.view.copy(places = listOf(fire))
    }

    override fun standInArea(inside: Boolean) {
        cooker.view = cooker.view.copy(atLocation = inside)
    }

    override fun emptyInput() {
        carried.bag -= RAW_TROUT
        cooker.raw = 0
        cooker.view = cooker.view.copy(rawSlot = null)
    }

    override fun supply(units: Int) {
        carried.bag += RAW_TROUT
        cooker.raw += units
        cooker.view = cooker.view.copy(rawSlot = 0)
    }
}

/** Make a shortbow (u) from logs with a knife (Fletching 5), the recipe built here so the fixture owns its level. */
class MakeFixture : ProcessFixture, ToolFixture {

    override val name = "make"

    override val tool = ToolNeed(word = null, mapOf(KNIFE to 1))

    override val level = LevelNeed(Skill.FLETCHING, 5)

    override val carried = FakeCarried().apply {
        bag += setOf(KNIFE, LOGS)
        levels[level.skill] = level.level
    }

    private val recipe = Recipe(SHORTBOW_U, "Shortbow (u)", Skill.FLETCHING, level.level, listOf(RecipeWay(KNIFE, LOGS, mapOf(LOGS to 1), setOf(KNIFE))))
    private val maker = FakeMaker(MakeView(useSlot = 0, onSlot = 1, windowOpen = false, productOption = null))

    override val done: List<String> get() = maker.steps

    override fun turn(amount: Int?): StepTurn =
        turnOf(MakeWork(maker), amount, MakeStep(recipe).needs(), carried, fillsBag = false, FakeWalker(HERE))

    override fun gain(units: Int) {
        maker.products += units
    }

    override fun adapterBusy(busy: Boolean) {
        maker.busy = busy
    }

    override fun emptyInput() {
        carried.bag -= LOGS
        maker.view = maker.view.copy(onSlot = null)
    }

    override fun supply(units: Int) {
        carried.bag += LOGS
        maker.view = maker.view.copy(onSlot = 1)
    }

    override fun toString(): String = name
}

/** Smelt iron (Smithing 15) from iron ore, a furnace in reach. */
class SmeltFixture : SpotFixture("smelt", LevelNeed(Skill.SMITHING, 15), bag = setOf(IRON_ORE)), ProcessFixture {

    /** Luna's bars name their ores from the item definitions, which need the cache. */
    init {
        TestWorld.world
    }

    private val step = SmeltStep(BarType.IRON, RADIUS, WorkSpot.At(anchor))
    private val furnace = PlaceCandidate(FURNACE, targetTile, distance = 0, usableFromHere = true, anchor.toPosition())
    private val smelter = FakeSmelter(SmeltingView(oreSlot = 0, atLocation = true, listOf(furnace)), anchor)

    override val done: List<String> get() = smelter.steps

    override fun turn(amount: Int?): StepTurn = turnOf(SmeltingWork(smelter), amount, step.needs(), carried, fillsBag = false, walker)

    override fun gain(units: Int) {
        smelter.bars += units
    }

    override fun adapterBusy(busy: Boolean) {
        smelter.busy = busy
    }

    override fun clearTargets() {
        smelter.view = smelter.view.copy(furnaces = emptyList())
    }

    override fun restoreTargets() {
        smelter.view = smelter.view.copy(furnaces = listOf(furnace))
    }

    override fun standInArea(inside: Boolean) {
        smelter.view = smelter.view.copy(atLocation = inside)
    }

    override fun emptyInput() {
        carried.bag -= IRON_ORE
        smelter.view = smelter.view.copy(oreSlot = null)
    }

    override fun supply(units: Int) {
        carried.bag += IRON_ORE
        smelter.view = smelter.view.copy(oreSlot = 0)
    }
}

/** Smith an iron dagger (Smithing 15) with a hammer, an anvil in reach. */
class SmithFixture : SpotFixture("smith", LevelNeed(Skill.SMITHING, 15), bag = setOf(HAMMER, IRON_BAR)), ProcessFixture, ToolFixture {

    /** Luna's smithing tables name their items from the item definitions, which need the cache. */
    init {
        TestWorld.world
    }

    override val tool = ToolNeed(word = null, mapOf(HAMMER to 1))

    private val step = SmithStep(BarType.IRON, SmithingTable.DAGGER, RADIUS, WorkSpot.At(anchor))
    private val anvil = PlaceCandidate(ANVIL, targetTile, distance = 0, usableFromHere = true, anchor.toPosition())
    private val smither = FakeSmither(SmithingView(barSlot = 0, windowOpen = false, atLocation = true, listOf(anvil)), anchor)

    override val done: List<String> get() = smither.steps

    override fun turn(amount: Int?): StepTurn = turnOf(SmithingWork(smither), amount, step.needs(), carried, fillsBag = false, walker)

    override fun gain(units: Int) {
        smither.made += units
    }

    override fun adapterBusy(busy: Boolean) {
        smither.busy = busy
    }

    override fun clearTargets() {
        smither.view = smither.view.copy(anvils = emptyList())
    }

    override fun restoreTargets() {
        smither.view = smither.view.copy(anvils = listOf(anvil))
    }

    override fun standInArea(inside: Boolean) {
        smither.view = smither.view.copy(atLocation = inside)
    }

    override fun emptyInput() {
        carried.bag -= IRON_BAR
        smither.view = smither.view.copy(barSlot = null)
    }

    override fun supply(units: Int) {
        carried.bag += IRON_BAR
        smither.view = smither.view.copy(barSlot = 0)
    }
}
