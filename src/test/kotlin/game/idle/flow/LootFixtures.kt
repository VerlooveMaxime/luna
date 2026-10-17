package game.idle.flow

import game.idle.autopilot.Candidate
import game.idle.autopilot.fighting.FakeFighter
import game.idle.autopilot.fighting.FightStep
import game.idle.autopilot.fighting.FightTarget
import game.idle.autopilot.fighting.FightView
import game.idle.autopilot.fighting.FightWork
import game.idle.autopilot.pickup.FakePicker
import game.idle.autopilot.pickup.PickUpStep
import game.idle.autopilot.pickup.PickUpView
import game.idle.autopilot.pickup.PickUpWork
import game.idle.autopilot.travel.FakeTraveller
import game.idle.location.Tile
import game.idle.movement.DoorStep
import io.luna.game.model.Position

/**
 * The kinds that fight npcs and take what they drop: fight, an area kind whose Amount counts kills, which waits for an
 * npc when none is in reach and whose last kill must finish dying before the step ends; pick up, whose Amount counts
 * pickups, which fills the bag but is not [Gathers] (it says what it leaves lying, so a full bag is its own to report)
 * and has no area the turn walks back to (it walks there once by its own rule, then ends). Their needs and whether they
 * gather are read from a real resolved step of each.
 */
fun lootKinds(): List<AmountFixture> = listOf(FightFixture(), PickUpFixture())

private const val BONES = 526
private const val MAN = 1

/** Fight with one man next to the anchor, in reach from where the player stands; no kill dying until [settling]. */
class FightFixture : AreaFixture, SettlingFixture, ApproachFixture {

    override val name = "fight"

    override val carried = FakeCarried()

    override val anchor = Tile(3200, 3200)

    override val traveller = FakeTraveller(anchor.toPosition())

    private val step: ResolvedStep = FightStep(FightTarget("man", setOf(MAN), "Man", 2..2), radius = 10, WorkSpot.At(anchor))
    private val man = Candidate(id = 7, Position(anchor.x + 1, anchor.y), distance = 0, usableFromHere = true, approach = anchor.toPosition())
    private val fighter = FakeFighter(FightView(atLocation = true, listOf(man)), anchor)

    override val done: List<String> get() = fighter.steps

    override fun turn(amount: Int?): StepTurn =
        turnOf(FightWork(fighter), amount, step.needs(), carried, fillsBag = step is Gathers, traveller)

    override fun gain(units: Int) {
        fighter.kills += units
    }

    override fun adapterBusy(busy: Boolean) {
        fighter.busy = busy
    }

    override fun settling(settling: Boolean) {
        fighter.lastKillDying = settling
    }

    override fun clearTargets() {
        fighter.view = fighter.view.copy(targets = emptyList())
    }

    override fun restoreTargets() {
        fighter.view = fighter.view.copy(targets = listOf(man))
    }

    override fun targetFarOff(door: DoorStep?) {
        val farOff = man.copy(
            position = Position(anchor.x + 6, anchor.y), distance = 12, usableFromHere = false, approach = Position(anchor.x + 5, anchor.y), door = door,
        )
        fighter.view = fighter.view.copy(targets = listOf(farOff))
    }

    override fun leaveArea() {
        fighter.view = fighter.view.copy(atLocation = false)
        traveller.position = Position(anchor.x + 20, anchor.y)
    }

    override fun enterArea() {
        fighter.view = fighter.view.copy(atLocation = true)
        traveller.position = anchor.toPosition()
    }

    override fun toString(): String = name
}

/** Pick up at the work spot with bones lying underfoot that fit the bag. */
class PickUpFixture : AmountFixture, ApproachFixture {

    override val name = "pick up"

    override val carried = FakeCarried()

    private val here = Tile(3200, 3200)
    private val step: ResolvedStep = PickUpStep(setOf(BONES), radius = 10, WorkSpot.At(here))
    private val bones = Candidate(BONES, here.toPosition(), distance = 0, usableFromHere = true, approach = here.toPosition())
    private val picker = FakePicker(PickUpView(atLocation = true, listOf(bones)))

    override val done: List<String> get() = picker.steps

    override fun turn(amount: Int?): StepTurn =
        turnOf(PickUpWork(picker), amount, step.needs(), carried, fillsBag = step is Gathers, FakeTraveller(here.toPosition()))

    override fun gain(units: Int) {
        picker.pickedUp += units
    }

    override fun adapterBusy(busy: Boolean) {
        picker.busy = busy
    }

    override fun targetFarOff(door: DoorStep?) {
        val tile = Position(here.x + 6, here.y)
        picker.view = picker.view.copy(items = listOf(bones.copy(position = tile, distance = 12, usableFromHere = false, approach = tile, door = door)))
    }

    override fun toString(): String = name
}
