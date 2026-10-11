package game.idle.autopilot.pickup

import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaPickerTest {

    private val anchor = Position(3200, 3200)
    private val bones = 526
    private val cowhide = 1739
    private val logs = 1511
    private val coins = 995
    private val deadTree = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("picker", position)

    private fun picker(player: Player) = LunaPicker(player, setOf(bones, cowhide, coins), Area(Tile.of(anchor), radius = 10))

    private fun takes(): MutableList<Int> {
        val taken = mutableListOf<Int>()
        TestWorld.listen(PickupItemEvent::class.java) { taken += it.groundItem.id }
        return taken
    }

    /** Luna's pickup as `pickupItem.kts` does it: off the ground and into the bag. */
    private fun pickingUp(player: Player) =
        TestWorld.listen(PickupItemEvent::class.java) {
            TestWorld.world.items.unregister(it.groundItem)
            player.inventory.add(it.groundItem.toItem())
        }

    private fun fillBag(player: Player) = repeat(28) { player.inventory.add(Item(logs)) }

    @Test
    fun `an idle player is not busy`() {
        assertFalse(picker(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(picker(player).isBusy())
    }

    @Test
    fun `a take on its way keeps the player busy`() {
        val player = login()
        takes()
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, Position(3206, 3200))

        picker.take(picker.look().items.single())

        assertTrue(picker.isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(picker(player).isBusy())
    }

    @Test
    fun `an item of the step underfoot is seen where it lies, fitting the bag`() {
        val player = login()
        TestWorld.dropItem(bones, 1, anchor)

        assertEquals(listOf(GroundLoot(bones, anchor, distance = 0, fits = true)), picker(player).look().items)
    }

    @Test
    fun `an item further off is as far as the walk onto its tile`() {
        val player = login()
        TestWorld.dropItem(cowhide, 1, Position(3203, 3201))

        assertEquals(3, picker(player).look().items.single().distance)
    }

    @Test
    fun `items the step does not take are not seen`() {
        val player = login()
        TestWorld.dropItem(logs, 1, anchor)

        assertEquals(emptyList<GroundLoot>(), picker(player).look().items)
    }

    @Test
    fun `the player's own drop is seen`() {
        val player = login()
        TestWorld.dropItem(bones, 1, anchor, viewer = player)

        assertEquals(1, picker(player).look().items.size)
    }

    @Test
    fun `another player's drop is not seen before it turns public`() {
        val player = login()
        TestWorld.dropItem(bones, 1, anchor, viewer = TestWorld.login("other", Position(3205, 3205)))

        assertEquals(emptyList<GroundLoot>(), picker(player).look().items)
    }

    @Test
    fun `an item outside the area is not seen`() {
        val player = login()
        TestWorld.dropItem(bones, 1, Position(3211, 3200))

        assertEquals(emptyList<GroundLoot>(), picker(player).look().items)
    }

    @Test
    fun `an item on a tile nobody can stand on is not seen`() {
        val player = login()
        TestWorld.place(deadTree, Position(3203, 3200))
        TestWorld.dropItem(bones, 1, Position(3203, 3200))

        assertEquals(emptyList<GroundLoot>(), picker(player).look().items)
    }

    @Test
    fun `an item a full bag has no room for does not fit`() {
        val player = login()
        fillBag(player)
        TestWorld.dropItem(bones, 1, anchor)

        assertFalse(picker(player).look().items.single().fits)
    }

    @Test
    fun `coins fit a full bag holding coins`() {
        val player = login()
        repeat(27) { player.inventory.add(Item(logs)) }
        player.inventory.add(Item(coins, 10))
        TestWorld.dropItem(coins, 25, anchor)

        assertTrue(picker(player).look().items.single().fits)
    }

    @Test
    fun `the view knows when the player is in the area`() {
        assertTrue(picker(login(Position(3210, 3200))).look().atLocation)
    }

    @Test
    fun `the view knows when the player is away from the area, still seeing what lies there`() {
        val player = login(Position(3215, 3200))
        TestWorld.dropItem(bones, 1, anchor)

        assertEquals(listOf(false, 1), picker(player).look().let { listOf(it.atLocation, it.items.size) })
    }

    @Test
    fun `a player too far from the area sees nothing there`() {
        val player = login(Position(3250, 3200))
        TestWorld.dropItem(bones, 1, anchor)

        assertEquals(PickUpView(atLocation = false, items = emptyList()), picker(player).look())
    }

    @Test
    fun `a player on another floor sees nothing there`() {
        val player = login(Position(3200, 3200, 1))
        TestWorld.dropItem(bones, 1, anchor)

        assertEquals(PickUpView(atLocation = false, items = emptyList()), picker(player).look())
    }

    @Test
    fun `taking an item clicks it like the client`() {
        val player = login()
        val taken = takes()
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, anchor)

        picker.take(picker.look().items.single())
        TestWorld.tick()

        assertEquals(listOf(bones), taken)
    }

    @Test
    fun `an item gone since the look is not taken`() {
        val player = login()
        val taken = takes()
        val picker = picker(player)
        val item = TestWorld.dropItem(bones, 1, anchor)
        val seen = picker.look().items.single()
        TestWorld.world.items.unregister(item)

        picker.take(seen)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), taken)
    }

    @Test
    fun `another item where it lay is not taken`() {
        val player = login()
        val taken = takes()
        val picker = picker(player)
        TestWorld.dropItem(logs, 1, anchor)

        picker.take(GroundLoot(bones, anchor, distance = 0, fits = true))
        TestWorld.tick()

        assertEquals(emptyList<Int>(), taken)
    }

    @Test
    fun `an item the player cannot see where it lay is not taken`() {
        val player = login()
        val taken = takes()
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, anchor, viewer = TestWorld.login("other", Position(3205, 3205)))

        picker.take(GroundLoot(bones, anchor, distance = 0, fits = true))
        TestWorld.tick()

        assertEquals(emptyList<Int>(), taken)
    }

    @Test
    fun `nothing taken is nothing picked up`() {
        assertEquals(0, picker(login()).pickedUp())
    }

    @Test
    fun `an item taken into the bag is picked up`() {
        val player = login()
        pickingUp(player)
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, anchor)
        picker.take(picker.look().items.single())

        TestWorld.tick()

        assertEquals(1, picker.pickedUp())
    }

    @Test
    fun `a pile of coins counts one pickup`() {
        val player = login()
        pickingUp(player)
        val picker = picker(player)
        TestWorld.dropItem(coins, 25, anchor)
        picker.take(picker.look().items.single())
        TestWorld.tick()

        picker.pickedUp()

        assertEquals(1, picker.pickedUp())
    }

    @Test
    fun `two piles alike on one tile count two pickups`() {
        val player = login()
        pickingUp(player)
        val picker = picker(player)
        TestWorld.dropItem(coins, 10, anchor)
        TestWorld.dropItem(coins, 10, anchor)
        picker.take(picker.look().items.last())
        TestWorld.tick()
        picker.pickedUp()

        picker.take(picker.look().items.single())
        TestWorld.tick()

        assertEquals(2, picker.pickedUp())
    }

    @Test
    fun `a take still on its way is not counted yet`() {
        val player = login()
        pickingUp(player)
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, Position(3206, 3200))

        picker.take(picker.look().items.single())

        assertEquals(0, picker.pickedUp())
    }

    @Test
    fun `a take that got nothing into the bag is no pickup`() {
        val player = login()
        takes()
        val picker = picker(player)
        TestWorld.dropItem(bones, 1, anchor)
        picker.take(picker.look().items.single())

        TestWorld.tick()

        assertEquals(0, picker.pickedUp())
    }

    @Test
    fun `walking to an item heads onto its tile`() {
        val player = login()
        TestWorld.dropItem(cowhide, 1, Position(3204, 3200))
        val picker = picker(player)

        picker.walkTo(picker.look().items.single())

        assertEquals(Position(3204, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `walking to the area heads for its anchor`() {
        val player = login(Position(3220, 3200))

        picker(player).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `a pick-up step picks up through a Luna picker`() {
        val step = PickUpStep(setOf(bones), radius = 10, WorkSpot.RunTile)

        assertInstanceOf(PickUpActivity::class.java, step.activity(login(), Tile(3200, 3200)))
    }

    @Test
    fun `a pick-up step's activity says what a full bag left lying`() {
        val player = login()
        fillBag(player)
        TestWorld.dropItem(bones, 1, anchor)
        val activity = PickUpStep(setOf(bones), radius = 10, WorkSpot.RunTile).activity(player, Tile(3200, 3200))

        activity.act()

        assertEquals(Said.Ended(Reason.BagFull(listOf(bones))), activity.said())
    }
}
