package game.harness

import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.GameChatboxMessageWriter
import io.luna.net.msg.out.NpcUpdateMessageWriter
import io.luna.net.msg.out.PlayerUpdateMessageWriter
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek

class MessageLogTest {

    private var tick = 40L
    private val log = MessageLog(capacity = 3) { tick }

    private open class BaseWriter : GameMessageWriter() {
        private val inherited = "from the base"

        override fun write(player: Player?, buffer: ByteBuf?): ByteMessage = throw UnsupportedOperationException()
    }

    @Suppress("unused")
    private class FieldsWriter : BaseWriter() {
        private val count = 3
        private val flag = true
        private val letter = 'x'
        private val builder = StringBuilder("built")
        private val day = DayOfWeek.MONDAY
        private val reference = Any()
        private val missing: String? = null

        companion object {
            const val SHARED = "static"
        }
    }

    private fun chat(text: String) = GameChatboxMessageWriter(text)

    private fun encodedChat(text: String): GameMessage {
        val payload = Unpooled.copiedBuffer("$text\n", StandardCharsets.ISO_8859_1)
        return GameMessage(63, MessageType.VAR, ByteMessage.wrap(payload))
    }

    @Test
    fun `records the writer type, tick and fields`() {
        log.record(chat("You get some logs."))

        assertEquals(
            listOf(RecordedMessage(1, 40, "GameChatboxMessageWriter", mapOf("message" to "You get some logs."))),
            log.since(0).messages,
        )
    }

    @Test
    fun `records an encoded message with its decoded type and fields`() {
        log.record(encodedChat("You get some logs."))

        assertEquals(
            listOf(RecordedMessage(1, 40, "GameChatboxMessageWriter", mapOf("message" to "You get some logs."))),
            log.since(0).messages,
        )
    }

    @Test
    fun `encoded player updating is not recorded`() {
        log.record(GameMessage(90, MessageType.VAR_SHORT, ByteMessage.wrap(Unpooled.buffer())))

        assertEquals(emptyList<RecordedMessage>(), log.since(0).messages)
    }

    @Test
    fun `writers and encoded messages share one numbering`() {
        log.record(chat("a"))
        log.record(encodedChat("b"))

        assertEquals(listOf(1L, 2L), log.since(0).messages.map { it.seq })
    }

    @Test
    fun `numbers messages in order`() {
        log.record(chat("a"))
        log.record(chat("b"))

        assertEquals(listOf(1L, 2L), log.since(0).messages.map { it.seq })
    }

    @Test
    fun `since returns only newer messages`() {
        log.record(chat("old"))
        log.record(chat("new"))

        assertEquals(listOf(mapOf("message" to "new")), log.since(1).messages.map { it.fields })
    }

    @Test
    fun `next is zero for an empty log`() {
        assertEquals(0L, log.since(0).next)
    }

    @Test
    fun `next is the newest sequence number whatever the filter`() {
        log.record(chat("a"))
        log.record(chat("b"))

        assertEquals(2L, log.since(0, type = "nothing matches").next)
    }

    @Test
    fun `type filter matches part of the type ignoring case`() {
        log.record(chat("a"))
        log.record(FieldsWriter())

        assertEquals(listOf("GameChatboxMessageWriter"), log.since(0, type = "chatbox").messages.map { it.type })
    }

    @Test
    fun `the oldest message goes when the log is full`() {
        (1..4).forEach { log.record(chat("line $it")) }

        assertEquals(listOf(2L, 3L, 4L), log.since(0).messages.map { it.seq })
    }

    @Test
    fun `tick is read when the message is recorded`() {
        log.record(chat("a"))
        tick = 41

        assertEquals(40L, log.since(0).messages.single().tick)
    }

    @Test
    fun `player updating is not recorded`() {
        log.record(PlayerUpdateMessageWriter(emptyList()))

        assertEquals(emptyList<RecordedMessage>(), log.since(0).messages)
    }

    @Test
    fun `npc updating is not recorded`() {
        log.record(NpcUpdateMessageWriter(emptyList()))

        assertEquals(emptyList<RecordedMessage>(), log.since(0).messages)
    }

    @Test
    fun `only plain fields are kept, including inherited ones`() {
        log.record(FieldsWriter())

        assertEquals(
            mapOf(
                "count" to 3,
                "flag" to true,
                "letter" to 'x',
                "builder" to "built",
                "day" to "MONDAY",
                "inherited" to "from the base",
            ),
            log.since(0).messages.single().fields,
        )
    }

    @Test
    fun `capacity must be positive`() {
        assertThrows<IllegalArgumentException> { MessageLog(capacity = 0) { 0 } }
    }
}
