package game.idle.ui

import game.harness.encode
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.StepOption
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

class SearchMessagesTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() {
        encoded.forEach { it.payload.releaseAll() }
        TestWorld.reset()
    }

    private fun encoded(writer: GameMessageWriter) = encode(writer).also { encoded += it }

    private fun bytes(message: GameMessage): List<Int> {
        val buffer = message.payload.buffer
        return (buffer.readerIndex() until buffer.writerIndex()).map { buffer.getByte(it).toInt() and 0xff }
    }

    private fun text(line: String): List<Int> = line.toByteArray(StandardCharsets.ISO_8859_1).map { it.toInt() } + 10

    private fun rows(vararg rows: SearchRow) = encoded(SearchRowsMessageWriter(4, "", rows.size, 3, 0, rows.toList()))

    /** A page's header before its row count: serial, "" query, total, columns, offset. */
    private val headerSize = 1 + 1 + 2 + 1 + 2

    private fun clientPacket(opcode: Int, payload: ByteMessage) = GameMessage(opcode, MessageType.FIXED, payload)

    @Test
    fun `the prompt opens with the search opcode and a short length`() {
        val message = encoded(SearchOpenMessageWriter(4, "Which tree?", ""))

        assertEquals(listOf<Any>(103, MessageType.VAR_SHORT), listOf(message.opcode, message.type))
    }

    @Test
    fun `the prompt carries its serial, its title and its line for an empty list`() {
        assertEquals(listOf(4) + text("Which tree?") + text("None"), bytes(encoded(SearchOpenMessageWriter(4, "Which tree?", "None"))))
    }

    @Test
    fun `rows go with the rows opcode and a short length`() {
        val message = rows()

        assertEquals(listOf<Any>(105, MessageType.VAR_SHORT), listOf(message.opcode, message.type))
    }

    @Test
    fun `a page starts with its serial, query, total, columns and offset`() {
        val message = encoded(SearchRowsMessageWriter(4, "oak", 300, 2, 258, emptyList()))

        assertEquals(listOf(4) + text("oak") + listOf(1, 44, 2, 1, 2, 0, 0), bytes(message))
    }

    @Test
    fun `rows are a count, then each row's index, flags, label, note and picture`() {
        val message = rows(SearchRow(258, "Oak", "Woodcutting 15", false, WidgetPicture.Item(1521)))

        assertEquals(listOf(0, 1, 1, 2, 0) + text("Oak") + text("Woodcutting 15") + listOf(2, 0x05, 0xf1), bytes(message).drop(headerSize))
    }

    @Test
    fun `a greyed row sets the greyed bit`() {
        val message = rows(SearchRow(0, "Yew", "needs Woodcutting 60", true, WidgetPicture.None))

        assertEquals(SearchRowsMessageWriter.GREYED, bytes(message)[headerSize + 4])
    }

    @Test
    fun `a row takes the bytes the packet bound counts`() {
        val option = StepOption("woodcutting", "Woodcutting", OptionIcon.Skill(Skill.WOODCUTTING), note = "level 12")

        assertEquals(headerSize + 2 + SearchPrompt.PACKET.size(option), bytes(rows(SearchRow.of(0, option))).size)
    }

    @Test
    fun `a bank row takes the bytes the packet bound counts`() {
        val option = StepOption("lumbridge", "Lumbridge", OptionIcon.Bank)

        assertEquals(headerSize + 2 + SearchPrompt.PACKET.size(option), bytes(rows(SearchRow.of(0, option))).size)
    }

    @Test
    fun `a page request reads the serial, offset, count and query`() {
        val player = TestWorld.login("pager", Position(3200, 3200))
        val payload = ByteMessage.raw().put(7).putShort(300).put(15).putString("oak logs")

        val event = SearchPageMessageReader().decode(player, GameMessage(102, MessageType.VAR, payload))

        assertEquals(listOf<Any>(7, 300, 15, "oak logs"), listOf(event.serial, event.offset, event.count, event.query))
    }

    @Test
    fun `a pick reads the prompt's serial and the row's index`() {
        val player = TestWorld.login("picker", Position(3200, 3200))

        val event = SearchPickMessageReader().decode(player, clientPacket(103, ByteMessage.raw().put(200).putShort(300)))

        assertEquals(listOf(200, 300), listOf(event.serial, event.index))
    }

    @Test
    fun `a close reads the prompt's serial`() {
        val player = TestWorld.login("closer", Position(3200, 3200))

        val event = SearchClosedMessageReader().decode(player, clientPacket(105, ByteMessage.raw().put(201)))

        assertEquals(201, event.serial)
    }
}
