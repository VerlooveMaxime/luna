package game.idle.ui

import game.harness.encode
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StickyChatboxMessageWriterTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() = encoded.forEach { it.payload.releaseAll() }

    private fun encoded(id: Int) = encode(StickyChatboxMessageWriter(id)).also { encoded += it }

    private fun payload(id: Int): ByteArray {
        val buffer = encoded(id).payload.buffer
        return ByteArray(buffer.readableBytes()).also { buffer.getBytes(0, it) }
    }

    @Test
    fun `the message is a fixed size packet with the sticky chatbox opcode`() {
        val message = encoded(6179)

        assertEquals(StickyChatboxMessageWriter.OPCODE, message.opcode)
        assertEquals(MessageType.FIXED, message.type)
    }

    @Test
    fun `the interface id is sent low byte first`() {
        assertArrayEquals(byteArrayOf(0x23, 0x18), payload(6179))
    }

    @Test
    fun `no interface is sent as minus one`() {
        assertArrayEquals(byteArrayOf(-1, -1), payload(StickyChatboxMessageWriter.NONE))
    }
}
