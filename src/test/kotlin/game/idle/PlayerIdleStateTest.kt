package game.idle

import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerIdleStateTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a player starts with an empty idle state`() {
        val player = TestWorld.login("idler", Position(3200, 3200))

        assertEquals(IdleState(), player.idleState)
    }

    @Test
    fun `the idle state a player is given is the one read back`() {
        val player = TestWorld.login("idler", Position(3200, 3200))
        val state = IdleState(flow = listOf("bank @draynor"), stepIndex = 1, running = true)

        player.idleState = state

        assertEquals(state, player.idleState)
    }

    @Test
    fun `the idle state is saved with the player under idle_state`() {
        val player = TestWorld.login("idler", Position(3200, 3200))
        player.idleState = IdleState().started()

        val savedKeys = player.attributes.save().keys.map { it.substringBefore("@") }

        assertTrue("idle_state" in savedKeys)
    }
}
