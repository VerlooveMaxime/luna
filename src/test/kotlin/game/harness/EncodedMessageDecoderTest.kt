package game.harness

import game.idle.ui.HintArrowMessageWriter
import game.idle.ui.MapPickMessageWriter
import game.idle.ui.PictureMessageWriter
import game.idle.ui.PromptMode
import game.idle.ui.SearchOpenMessageWriter
import game.idle.ui.WidgetColourMessageWriter
import game.idle.ui.SearchRow
import game.idle.ui.SearchRowsMessageWriter
import game.idle.ui.SlotCountMessageWriter
import game.idle.ui.StatusOverlayMessageWriter
import game.idle.ui.StickyChatboxMessageWriter
import game.idle.ui.TileEdge
import game.idle.ui.WidgetPicture
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
    fun `a map pick request decodes as the writer records it`() {
        assertDecodesAsRecorded(MapPickMessageWriter(3086, 3233))
    }

    @Test
    fun `an emptied picture decodes as the writer records it`() {
        assertDecodesAsRecorded(PictureMessageWriter(30430, WidgetPicture.None))
    }

    @Test
    fun `a sprite picture decodes as the writer records it`() {
        assertDecodesAsRecorded(PictureMessageWriter(30430, WidgetPicture.Media("staticons", 17)))
    }

    @Test
    fun `an item picture decodes as the writer records it`() {
        assertDecodesAsRecorded(PictureMessageWriter(30491, WidgetPicture.Item(1059)))
    }

    @Test
    fun `an npc picture decodes as the writer records it`() {
        assertDecodesAsRecorded(PictureMessageWriter(30581, WidgetPicture.NpcBody(81)))
    }

    @Test
    fun `an opened search decodes as the writer records it`() {
        assertDecodesAsRecorded(SearchOpenMessageWriter(7, PromptMode.SEARCH, "Which tree?", "Nothing to cut", "", 40))
    }

    @Test
    fun `a slot count decodes as the writer records it`() {
        assertDecodesAsRecorded(SlotCountMessageWriter(SlotCountMessageWriter.SAVED_FLOW_SLOTS, 6))
    }

    @Test
    fun `a widget colour decodes as the writer records it`() {
        assertDecodesAsRecorded(WidgetColourMessageWriter(31018, 0x5c5243))
    }

    @Test
    fun `an opened name prompt decodes as the writer records it`() {
        assertDecodesAsRecorded(SearchOpenMessageWriter(8, PromptMode.NAME, "Save over 'Cows' as:", "", "Cows", 20))
    }

    @Test
    fun `search rows of every picture kind decode as the writer records them`() {
        assertDecodesAsRecorded(
            SearchRowsMessageWriter(
                9, "oak", 140, 2, 30,
                listOf(
                    SearchRow(0, "Nearest bank", "", false, WidgetPicture.Media("mapfunction", 5)),
                    SearchRow(1, "Oak", "Woodcutting 15", false, WidgetPicture.Item(1521)),
                    SearchRow(2, "Cow", "level 2", false, WidgetPicture.NpcBody(81)),
                    SearchRow(3, "Yew", "needs Woodcutting 60", true, WidgetPicture.None),
                ),
            ),
        )
    }

    @Test
    fun `no search rows decode as the writer records them`() {
        assertDecodesAsRecorded(SearchRowsMessageWriter(0, "", 0, 3, 0, emptyList()))
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
