package game.idle.autopilot.pickup

import engine.spawn.ItemSpawn
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.ItemCatalog
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionFacts
import game.idle.flow.option.OptionIcon
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PickUpStepTypeTest {

    private val picked = Tile(3101, 3509)
    private val cow = 81
    private val bones = 526
    private val notedBones = 527
    private val cowhide = 1739
    private val feather = 314
    private val egg = 1944
    private val pen = Tile(3253, 3270)
    private val drops = DropLookup { if (it == cow) setOf(notedBones, cowhide) else emptySet() }
    private val spawns = SpawnCatalog(listOf(ItemSpawn(egg, 1, Position(3255, 3270), 100)))
    private val items = ItemCatalog(
        mapOf(bones to "Bones", cowhide to "Cowhide", feather to "Feather", egg to "Egg"),
        notes = mapOf(notedBones to bones),
    )
    private val type = PickUpStepType(drops, spawns, items, FakeNames(items = mapOf(bones to "Bones", cowhide to "Cowhide", feather to "Feather", egg to "Egg")))
    private val afterCows = FlowContext(fought = setOf(cow))

    private fun pickUp(vararg values: Pair<String, String>) = StepSettings("pickup", mapOf(*values))

    @Test
    fun `a pick-up step with nothing picked reads as pick up alone`() {
        assertEquals("pick up", type.summary(pickUp()))
    }

    @Test
    fun `a pick-up step's summary names its count, its items and its distance`() {
        assertEquals("pick up 5 bones, cowhide within 15", type.summary(pickUp("amount" to "5", "items" to "$bones,$cowhide", "within" to "15")))
    }

    @Test
    fun `pick up takes the items picked, their notes too, all lying around the flow's work spot`() {
        val step = type.resolve(pickUp("items" to "$bones,$cowhide"), FlowContext())

        assertEquals(PickUpStep(setOf(bones, notedBones, cowhide), radius = 10, WorkSpot.RunTile), step)
    }

    @Test
    fun `pick up takes a count of items within its distance of the walk step's tile`() {
        val step = type.resolve(pickUp("items" to "$egg", "amount" to "3", "within" to "4"), FlowContext(WorkSpot.At(pen)))

        assertEquals(PickUpStep(setOf(egg), radius = 4, WorkSpot.At(pen), amount = 3), step)
    }

    @Test
    fun `ids that name no item are left out`() {
        assertEquals(PickUpStep(setOf(egg), radius = 10, WorkSpot.RunTile), type.resolve(pickUp("items" to "99999,$egg"), FlowContext()))
    }

    @Test
    fun `pick up with no items picked cannot work`() {
        val error = assertThrows<FlowError> { type.resolve(pickUp(), FlowContext()) }

        assertEquals("pick up needs items picked", error.message)
    }

    @Test
    fun `the configure screen types the amount, lists the items, picks the work spot and types the distance`() {
        assertEquals(
            listOf(
                "Amount (left): typed amount 1..2147483647, button 'All'",
                "Items (left): list items on 5 rows, 'What would you like to pick up?'",
                "Work spot (right): map spot, none 'From the flow'",
                "Within (right): typed within 1..32",
            ),
            described(type.fields(FakeNames())),
        )
    }

    @Test
    fun `a new pick-up step holds no items`() {
        assertEquals(StepSettings("pickup"), type.newSettings(afterCows))
    }

    @Test
    fun `a pick-up step shows the items it picks up`() {
        assertEquals(StepPick("Bones, cowhide", OptionIcon.Item(bones)), type.pick(pickUp("items" to "$bones,$cowhide"), FakeNames(mapOf(bones to "Bones", cowhide to "Cowhide"))))
    }

    @Test
    fun `a pick-up step with nothing picked shows no pick`() {
        assertEquals(StepPick(null, null), type.pick(pickUp(), FakeNames()))
    }

    @Test
    fun `a pick-up step shows leather gloves`() {
        assertEquals(StepIcon.Item(1059), type.icon(pickUp()))
    }

    @Test
    fun `a pick-up step's slot says it takes all lying around`() {
        assertEquals(listOf("all lying around"), type.details(pickUp(), FlowContext()))
    }

    @Test
    fun `a pick-up step's slot says how many a lap takes`() {
        assertEquals(listOf("5 per lap"), type.details(pickUp("amount" to "5"), FlowContext()))
    }

    @Test
    fun `a pick-up step has no target to search and no skill`() {
        assertEquals(listOf(null, null), listOf(type.target(FakeNames()), type.skill(pickUp())))
    }

    @Test
    fun `items the fight step before drops, noted or not, need no warning`() {
        assertEquals(emptyList<String>(), type.warnings(pickUp("items" to "$bones,$cowhide"), afterCows, OptionFacts()))
    }

    @Test
    fun `items nothing before drops and none spawns are warned about`() {
        assertEquals(
            listOf("No fight step before drops feather, egg, and none spawns here."),
            type.warnings(pickUp("items" to "$bones,$feather,$egg"), afterCows, OptionFacts()),
        )
    }

    @Test
    fun `an item that spawns around the walk step's tile needs no warning`() {
        assertEquals(emptyList<String>(), type.warnings(pickUp("items" to "$egg"), FlowContext(WorkSpot.At(pen)), OptionFacts()))
    }

    @Test
    fun `without a walk step an item that spawns around the player needs no warning`() {
        assertEquals(emptyList<String>(), type.warnings(pickUp("items" to "$egg"), FlowContext(), OptionFacts(here = Tile(3254, 3270))))
    }

    @Test
    fun `the steps after a pick-up step know what it picks up as gathered`() {
        val after = PickUpStep(setOf(bones, cowhide), radius = 10, WorkSpot.RunTile).after(FlowContext(gathered = setOf(egg)))

        assertEquals(setOf(egg, bones, cowhide), after.gathered)
    }

    @Test
    fun `a pick-up step needs nothing in the bag`() {
        assertEquals(emptyList<Any>(), PickUpStep(setOf(bones), radius = 10, WorkSpot.RunTile).needs())
    }

    @Test
    fun `the items list offers what the fight step before drops first`() {
        val field = type.fields(FakeNames())[1] as StepField.Items

        assertEquals(listOf("Bones", "Cowhide"), field.source.options(OptionContext(before = afterCows)).take(2).map { it.label })
    }

    @Test
    fun `a pick-up step with a picked work spot works around it, not where the flow went`() {
        val step = type.resolve(pickUp("items" to "$bones", "spot" to "3101 3509"), FlowContext(workSpot = WorkSpot.At(pen)))

        assertEquals(PickUpStep(setOf(bones, notedBones), 10, WorkSpot.Own(picked)), step)
    }

    @Test
    fun `a pick-up step's summary ends with its picked work spot`() {
        assertEquals("pick up bones at 3101 3509", type.summary(pickUp("items" to "$bones", "spot" to "3101 3509")))
    }

    @Test
    fun `the steps after a pick-up step with a picked work spot work there`() {
        val after = PickUpStep(setOf(bones), 12, WorkSpot.Own(picked)).after(FlowContext())

        assertEquals(WorkSpot.At(picked), after.workSpot)
    }

    @Test
    fun `a pick-up step walks to its picked spot from outside its area, and to no spot when it works where the flow went`() {
        val own = PickUpStep(setOf(bones), 12, WorkSpot.Own(picked))
        val flows = PickUpStep(setOf(bones), 12, WorkSpot.At(picked))

        assertEquals(listOf(Area(picked, 12), null), listOf(own.ownArea, flows.ownArea))
    }
}
