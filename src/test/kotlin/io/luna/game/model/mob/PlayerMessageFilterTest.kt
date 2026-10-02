package io.luna.game.model.mob

import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [Player.setMessageFilter], the IdleRS addition that lets a plugin keep game messages out of the chatbox. */
class PlayerMessageFilterTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun filtered(): Player =
        TestWorld.login("reader", Position(3200, 3200)).also { player -> player.setMessageFilter { it != "Hidden." } }

    @Test
    fun `a message the filter refuses never reaches the chatbox`() {
        val player = filtered()

        player.sendMessage("Hidden.")
        player.sendMessage("Shown.")

        assertEquals(listOf("Shown."), TestWorld.chatbox(player))
    }

    @Test
    fun `debugging lines are never filtered`() {
        val player = TestWorld.login("reader", Position(3200, 3200)).also { it.setMessageFilter { false } }

        player.sendDebugMessage("[FocusChangedMessageReader] focus: true")

        assertEquals(1, TestWorld.chatbox(player).size)
    }
}
