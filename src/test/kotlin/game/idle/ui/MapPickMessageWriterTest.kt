package game.idle.ui

import game.harness.encode
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MapPickMessageWriterTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() = encoded.forEach { it.payload.releaseAll() }

    private fun encoded(x: Int, y: Int) = encode(MapPickMessageWriter(x, y)).also { encoded += it }

    @Test
    fun `the message uses the map pick opcode with a fixed size`() {
        val message = encoded(3086, 3233)

        assertEquals(listOf<Any>(101, MessageType.FIXED), listOf(message.opcode, message.type))
    }

    @Test
    fun `the payload is the centre tile as two unsigned shorts`() {
        val message = encoded(3086, 3233)

        assertEquals(listOf(3086, 3233), listOf(message.payload.getShort(false), message.payload.getShort(false)))
    }
}
