package io.luna.game.model.mob

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.net.msg.out.GameChatboxMessageWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [Player.sendDebugMessage], the IdleRS addition that keeps debugging lines off the tutorial's help box. */
class PlayerDebugMessageTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a debugging line reaches the chatbox marked for the client`() {
        val player = TestWorld.login("debugger", Position(3200, 3200))

        player.sendDebugMessage("[FocusChangedMessageReader] focus: true")

        assertEquals(listOf("[FocusChangedMessageReader] focus: true" + GameChatboxMessageWriter.DEBUG_SUFFIX), TestWorld.chatbox(player))
    }
}
