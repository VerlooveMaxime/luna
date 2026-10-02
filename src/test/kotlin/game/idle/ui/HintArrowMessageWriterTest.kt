package game.idle.ui

import game.harness.encode
import io.luna.game.model.Position
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HintArrowMessageWriterTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() = encoded.forEach { it.payload.releaseAll() }

    private fun encoded(writer: GameMessageWriter) = encode(writer).also { encoded += it }

    private fun payload(writer: GameMessageWriter): ByteArray {
        val buffer = encoded(writer).payload.buffer
        return ByteArray(buffer.readableBytes()).also { buffer.getBytes(0, it) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `the message is a fixed size packet with the hint arrow opcode`() {
        val message = encoded(HintArrowMessageWriter.overNpc(index = 12))

        assertEquals(HintArrowMessageWriter.OPCODE, message.opcode)
        assertEquals(MessageType.FIXED, message.type)
    }

    @Test
    fun `an npc arrow sends its index padded to six bytes`() {
        assertArrayEquals(bytes(1, 0x01, 0x2C, 0, 0, 0), payload(HintArrowMessageWriter.overNpc(index = 300)))
    }

    @Test
    fun `a tile arrow sends its edge as the type, then x, y and height`() {
        val writer = HintArrowMessageWriter.overTile(Position(3098, 3107), TileEdge.WEST, height = 128)

        assertArrayEquals(bytes(3, 0x0C, 0x1A, 0x0C, 0x23, 128), payload(writer))
    }

    @Test
    fun `a tile arrow at the centre uses type two`() {
        val writer = HintArrowMessageWriter.overTile(Position(3101, 3092), TileEdge.CENTRE, height = 0)

        assertEquals(2, payload(writer)[0].toInt())
    }

    @Test
    fun `a hidden arrow is a type the client does not draw, padded to six bytes`() {
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0), payload(HintArrowMessageWriter.hidden()))
    }

    @Test
    fun `a tile arrow below the floor is refused`() {
        assertThrows<IllegalArgumentException> {
            HintArrowMessageWriter.overTile(Position(3098, 3107), TileEdge.CENTRE, height = -1)
        }
    }

    @Test
    fun `a tile arrow higher than a byte is refused`() {
        assertThrows<IllegalArgumentException> {
            HintArrowMessageWriter.overTile(Position(3098, 3107), TileEdge.CENTRE, height = 256)
        }
    }
}
