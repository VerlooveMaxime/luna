package game.idle.autopilot.cooking

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaCarried
import game.idle.autopilot.LunaClicks
import game.idle.flow.Preconditions
import game.idle.flow.Reason
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.cooking.cookFood.CookingInterface
import game.skill.cooking.cookFood.Food
import game.testworld.TestWorld
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
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
    private val rawLobster = 377
    private val fire = 2732
    private val firePosition = Position(3201, 3200)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("cook", position)

    private fun cooker(player: Player) = LunaCooker(player, setOf(rawShrimps, rawAnchovies), area)

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

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
                Candidate(fire, firePosition, distance = 0, usableFromHere = true, approach = anchor),
            )),
            cooker(player).look(),
        )
    }

    @Test
    fun `without raw food the view has none`() {
        assertNull(cooker(login()).look().rawSlot)
    }

    /** What the step needs and the raw food the cooker picks to cook, read from the same bag. */
    private fun neededAndCooked(player: Player): Pair<Reason?, Int?> {
        val step = CookStep(setOf(rawShrimps, rawLobster), 10, WorkSpot.At(area.anchor))
        return Preconditions.check(step.needs(), LunaCarried(player)) to LunaCooker(player, step.rawIds, area).look().rawSlot
    }

    @Test
    fun `raw lobster alone above the player's level blocks on the level and is not cooked`() {
        val player = login()
        player.inventory.add(Item(rawLobster))

        assertEquals(Reason.NeedsLevel(Skill.COOKING, 38) to null, neededAndCooked(player))
    }

    @Test
    fun `with raw shrimps beside the lobster the step can work and cooks the shrimps`() {
        val player = login()
        player.inventory.add(Item(rawLobster))
        player.inventory.add(Item(rawShrimps))

        assertEquals(null to 1, neededAndCooked(player))
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
    fun `walking to a fire heads for its approach tile`() {
        val player = login()
        TestWorld.place(fire, Position(3205, 3200))

        cooker(player).walkTo(cooker(player).look().places.single())

        assertEquals(Position(3204, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), cooker(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `raw counts every raw food of the step`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))

        assertEquals(3, cooker(player).raw())
    }

    @Test
    fun `interrupting breaks off the cooking in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        cooker(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
