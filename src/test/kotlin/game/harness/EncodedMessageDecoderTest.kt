package game.harness

import game.idle.ui.HintArrowMessageWriter
import game.idle.ui.StatusOverlayMessageWriter
import game.idle.ui.StickyChatboxMessageWriter
import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.CloseWindowsMessageWriter
import io.luna.net.msg.out.DialogueInterfaceMessageWriter
import io.luna.net.msg.out.FlashTabMessageWriter
import io.luna.net.msg.out.GameChatboxMessageWriter
import io.luna.net.msg.out.InterfaceMessageWriter
import io.luna.net.msg.out.InventoryOverlayMessageWriter
import io.luna.net.msg.out.LogoutMessageWriter
import io.luna.net.msg.out.NumberInputMessageWriter
import io.luna.net.msg.out.TextInputMessageWriter
import io.luna.net.msg.out.WalkableInterfaceMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EncodedMessageDecoderTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() {
        encoded.forEach { it.payload.releaseAll() }
    }

    private fun encoded(writer: GameMessageWriter): GameMessage = encode(writer).also { encoded += it }

    private fun message(opcode: Int, vararg bytes: Byte) =
        GameMessage(opcode, MessageType.FIXED, ByteMessage.wrap(Unpooled.wrappedBuffer(bytes)))

    /** What [MessageLog] records for a headless player, from the writer itself. */
    private fun recordedFromWriter(writer: GameMessageWriter): DecodedMessage {
        val log = MessageLog(capacity = 1) { 0 }
        log.record(writer)
        val recorded = log.since(0).messages.single()
        return DecodedMessage(recorded.type, recorded.fields)
    }

    private fun assertDecodesAsRecorded(writer: GameMessageWriter) {
        assertEquals(recordedFromWriter(writer), EncodedMessageDecoder.decode(encoded(writer)))
    }

    @Test
    fun `chat box text decodes to the writer's type and message`() {
        val decoded = EncodedMessageDecoder.decode(encoded(GameChatboxMessageWriter("You get some logs.")))

        assertEquals(DecodedMessage("GameChatboxMessageWriter", mapOf("message" to "You get some logs.")), decoded)
    }

    @Test
    fun `chat box text decodes as the writer records it`() {
        assertDecodesAsRecorded(GameChatboxMessageWriter("You get some logs."))
    }

    @Test
    fun `chat box text keeps characters above ASCII`() {
        val decoded = EncodedMessageDecoder.decode(encoded(GameChatboxMessageWriter("Café au lait")))

        assertEquals(mapOf("message" to "Café au lait"), decoded?.fields)
    }

    @Test
    fun `widget text decodes as the writer records it`() {
        assertDecodesAsRecorded(WidgetTextMessageWriter("Congratulations, you just advanced a level.", 4268))
    }

    @Test
    fun `status overlay text decodes as the writer records it`() {
        assertDecodesAsRecorded(StatusOverlayMessageWriter("@gre@Autopilot@whi@ step 1/2|@yel@chop oak @draynor_oaks"))
    }

    @Test
    fun `a sticky chatbox interface decodes as the writer records it`() {
        assertDecodesAsRecorded(StickyChatboxMessageWriter(6179))
    }

    @Test
    fun `an emptied sticky chatbox keeps its negative id`() {
        assertDecodesAsRecorded(StickyChatboxMessageWriter(StickyChatboxMessageWriter.NONE))
    }

    @Test
    fun `an npc hint arrow decodes as the writer records it`() {
        assertDecodesAsRecorded(HintArrowMessageWriter.overNpc(index = 300))
    }

    @Test
    fun `a tile hint arrow decodes as the writer records it`() {
        assertDecodesAsRecorded(HintArrowMessageWriter.overTile(Position(3089, 3092), TileEdge.EAST, height = 200))
    }

    @Test
    fun `a hidden hint arrow decodes as the writer records it`() {
        assertDecodesAsRecorded(HintArrowMessageWriter.hidden())
    }

    @Test
    fun `a flashing tab decodes as the writer records it`() {
        assertDecodesAsRecorded(FlashTabMessageWriter(TabIndex.INVENTORY))
    }

    @Test
    fun `an opened interface decodes as the writer records it`() {
        assertDecodesAsRecorded(InterfaceMessageWriter(5292))
    }

    @Test
    fun `an opened dialogue decodes as the writer records it`() {
        assertDecodesAsRecorded(DialogueInterfaceMessageWriter(4267))
    }

    @Test
    fun `a walkable interface decodes as the writer records it`() {
        assertDecodesAsRecorded(WalkableInterfaceMessageWriter(197))
    }

    @Test
    fun `a removed walkable interface keeps its negative id`() {
        assertDecodesAsRecorded(WalkableInterfaceMessageWriter(-1))
    }

    @Test
    fun `an inventory overlay decodes as the writer records it`() {
        assertDecodesAsRecorded(InventoryOverlayMessageWriter(5292, 5063))
    }

    @Test
    fun `closing windows decodes as the writer records it`() {
        assertDecodesAsRecorded(CloseWindowsMessageWriter())
    }

    @Test
    fun `a number input decodes as the writer records it`() {
        assertDecodesAsRecorded(NumberInputMessageWriter())
    }

    @Test
    fun `a text input decodes as the writer records it`() {
        assertDecodesAsRecorded(TextInputMessageWriter())
    }

    @Test
    fun `a logout decodes as the writer records it`() {
        assertDecodesAsRecorded(LogoutMessageWriter())
    }

    @Test
    fun `player updating is skipped`() {
        assertNull(EncodedMessageDecoder.decode(message(opcode = 90)))
    }

    @Test
    fun `npc updating is skipped`() {
        assertNull(EncodedMessageDecoder.decode(message(opcode = 71)))
    }

    @Test
    fun `another opcode keeps its number and size`() {
        val decoded = EncodedMessageDecoder.decode(message(85, 1, 2, 3))

        assertEquals(DecodedMessage("opcode 85", mapOf("opcode" to 85, "size" to 3)), decoded)
    }

    @Test
    fun `decoding leaves the payload for the encoder`() {
        val message = encoded(WidgetTextMessageWriter("Chop down", 1))

        EncodedMessageDecoder.decode(message)

        assertEquals(0 to 1, message.payload.buffer.readerIndex() to message.payload.refCnt())
    }

    @Test
    fun `a string without its terminator is refused`() {
        assertThrows<IndexOutOfBoundsException> { EncodedMessageDecoder.decode(message(63, 'h'.code.toByte())) }
    }

    @Test
    fun `a payload shorter than its layout is refused`() {
        assertThrows<IndexOutOfBoundsException> { EncodedMessageDecoder.decode(message(159, 1)) }
    }
}
