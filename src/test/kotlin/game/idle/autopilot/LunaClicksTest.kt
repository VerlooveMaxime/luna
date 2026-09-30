package game.idle.autopilot

import game.testworld.TestWorld
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaClicksTest {

    private val boothId = 2213
    private val boothTile = Position(3200, 3200)
    private val nextToBooth = Position(3199, 3200)

    /** An action that never finishes on its own. */
    private class Endless(player: Player, type: ActionType) : Action<Player>(player, type) {
        override fun run(): Boolean = false
    }

    private class Refusing(player: Player) : PlayerController(player) {
        override fun event(event: ControllableEvent): Boolean = false
    }

    private fun player() = TestWorld.login("clicker", nextToBooth)

    private fun clickBooth(player: Player) {
        val booth = TestWorld.place(boothId, boothTile)
        LunaClicks.clickObject(player, ObjectFirstClickEvent(player, booth), booth, ObjectFirstClickEvent::class.java)
    }

    private fun recordBoothClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a player standing still with nothing to do is not acting`() {
        assertFalse(LunaClicks.isActing(player()))
    }

    @Test
    fun `a walking player is acting`() {
        val player = player()
        player.walking.addStep(Direction.WEST)

        assertTrue(LunaClicks.isActing(player))
    }

    @Test
    fun `a player running a foreground action is acting`() {
        val player = player()
        player.submitAction(Endless(player, ActionType.WEAK))

        assertTrue(LunaClicks.isActing(player))
    }

    @Test
    fun `a soft action alone does not keep a player acting`() {
        val player = player()
        player.submitAction(Endless(player, ActionType.SOFT))

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `an unlocked player whose controller allows the event may act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)

        assertTrue(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a locked player may not act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)
        player.lock()

        assertFalse(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a player whose controller refuses the event may not act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)
        player.controllers.register(Refusing(player))

        assertFalse(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a click reaches the object's listeners on the next tick`() {
        val player = player()
        val clicked = recordBoothClicks()

        clickBooth(player)
        TestWorld.tick()

        assertEquals(listOf(boothId), clicked)
    }

    @Test
    fun `a click closes the open window first`() {
        val player = player()
        player.overlays.open(StandardInterface(5292))

        clickBooth(player)

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `a player who may not act does not click`() {
        val player = player()
        val clicked = recordBoothClicks()
        player.lock()

        clickBooth(player)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }
}
