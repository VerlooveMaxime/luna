package game.idle.autopilot.cooking

import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.cooking.cookFood.CookingInterface
import game.skill.cooking.cookFood.Food
import game.idle.autopilot.RefusingController
import game.testworld.TestWorld
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaCookerTest {

    private val anchor = Position(3200, 3200)
    private val area = Area(Tile(3200, 3200), radius = 10)
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val fire = 2732
    private val firePosition = Position(3201, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("cook", position)

    private fun cooker(player: Player) = LunaCooker(player, setOf(rawShrimps, rawAnchovies), area)

    @Test
    fun `an idle player is not busy`() {
        assertFalse(cooker(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(cooker(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(cooker(player).isBusy())
    }

    @Test
    fun `the cooking window does not keep the player busy, it is what the step answers`() {
        val player = login()
        player.overlays.open(CookingInterface(Food.SHRIMP, true, TestWorld.place(fire, firePosition)))

        assertFalse(cooker(player).isBusy())
        assertTrue(cooker(player).look().windowOpen)
    }

    @Test
    fun `the view finds the raw food and the fires around the work spot`() {
        val player = login()
        player.inventory.add(Item(1511))
        player.inventory.add(Item(rawShrimps))
        TestWorld.place(fire, firePosition)

        assertEquals(
            CookingView(rawSlot = 1, windowOpen = false, atLocation = true, places = listOf(
                PlaceCandidate(fire, firePosition, distance = 0, usableFromHere = true, approach = anchor),
            )),
            cooker(player).look(),
        )
    }

    /** Tutorial Island's kitchen: the range lies across the back of an alcove whose side walls flank the front tiles. */
    @Test
    fun `a long range at the back of an alcove is walked up to between the walls`() {
        val player = login(Position(3205, 3205))
        TestWorld.place(3039, Position(3205, 3200), direction = ObjectDirection.SOUTH)
        TestWorld.place(1902, Position(3205, 3201), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        TestWorld.place(1902, Position(3206, 3201), ObjectType.STRAIGHT_WALL, ObjectDirection.EAST)

        val place = cooker(player).look().places.single()

        assertTrue(place.approach in listOf(Position(3205, 3201), Position(3206, 3201)))
    }

    @Test
    fun `without raw food the view has none`() {
        assertNull(cooker(login()).look().rawSlot)
    }

    @Test
    fun `using the food on the fire goes through the same interaction as the client`() {
        val player = login()
        player.inventory.add(Item(rawShrimps))
        TestWorld.place(fire, firePosition)
        val used = mutableListOf<String>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += "${it.usedItemId} on ${it.objectId}" }

        cooker(player).useOn(cooker(player).look().places.single(), slot = 0)
        TestWorld.tick()

        assertEquals(listOf("317 on 2732"), used)
    }

    @Test
    fun `a fire that burnt out since the look is not used`() {
        val player = login()
        player.inventory.add(Item(rawShrimps))
        val burning = TestWorld.place(fire, firePosition)
        val seen = cooker(player).look().places.single()
        TestWorld.world.objects.unregister(burning)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.objectId }

        cooker(player).useOn(seen, slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `an empty slot is not used`() {
        val player = login()
        TestWorld.place(fire, firePosition)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.objectId }

        cooker(player).useOn(cooker(player).look().places.single(), slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `cook all presses the window's all button`() {
        val player = login()
        val pressed = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { pressed += it.id }

        cooker(player).cookAll()

        assertEquals(listOf(13717), pressed)
    }

    @Test
    fun `a click the controller refuses presses nothing`() {
        val player = login()
        player.controllers.register(RefusingController(player))
        val pressed = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { pressed += it.id }

        cooker(player).cookAll()

        assertEquals(emptyList<Int>(), pressed)
    }

    @Test
    fun `fires only another player sees and other objects are not places to cook`() {
        val player = login()
        val other = TestWorld.login("other", Position(3210, 3210))
        TestWorld.placeFor(other, fire, firePosition)
        TestWorld.place(1286, Position(3203, 3200))

        assertEquals(emptyList<PlaceCandidate>(), cooker(player).look().places)
    }

    @Test
    fun `a place whose object changed since the look is not used`() {
        val player = login()
        player.inventory.add(Item(rawShrimps))
        TestWorld.place(fire, firePosition)
        val seenAsRange = cooker(player).look().places.single().copy(objectId = 114)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.objectId }

        cooker(player).useOn(seenAsRange, slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `a fire only another player sees is not used`() {
        val player = login()
        player.inventory.add(Item(rawShrimps))
        val other = TestWorld.login("other", Position(3210, 3210))
        TestWorld.placeFor(other, fire, firePosition)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.objectId }

        cooker(player).useOn(PlaceCandidate(fire, firePosition, 0, usableFromHere = true, approach = anchor), slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `walking to a fire heads for its approach tile`() {
        val player = login()
        TestWorld.place(fire, Position(3205, 3200))

        cooker(player).walkTo(cooker(player).look().places.single())

        assertEquals(Position(3204, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `walking back heads for the anchor`() {
        val player = login(Position(3220, 3200))

        cooker(player).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `raw counts every raw food of the step`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))

        assertEquals(3, cooker(player).raw())
    }

    @Test
    fun `stopping ends the cooking in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        cooker(player).stop()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
