package game.idle.ui

import game.harness.encode
import io.luna.net.codec.MessageType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class StatusOverlayMessageWriterTest {

    private val encoded = mutableListOf<io.luna.net.msg.GameMessage>()

    @AfterEach
    fun releasePayloads() = encoded.forEach { it.payload.releaseAll() }

    private fun encoded(text: String) = encode(StatusOverlayMessageWriter(text)).also { encoded += it }

    @Test
    fun `the message uses the overlay opcode with a short length`() {
        val message = encoded("@gre@Autopilot")

        assertEquals(StatusOverlayMessageWriter.OPCODE, message.opcode)
        assertEquals(MessageType.VAR_SHORT, message.type)
    }

    @Test
    fun `the payload is the text followed by the string terminator`() {
        val message = encoded("step 1/2|chop oak")

        val bytes = ByteArray(message.payload.buffer.readableBytes()).also { message.payload.buffer.getBytes(0, it) }
        assertEquals("step 1/2|chop oak\n", String(bytes, StandardCharsets.ISO_8859_1))
    }
}
