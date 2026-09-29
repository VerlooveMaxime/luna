package game.harness

import io.luna.game.model.mob.bot.io.BotChannel
import io.luna.net.codec.IsaacCipher
import io.luna.net.client.GameClient
import io.luna.net.codec.game.GameMessageEncoder
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageRepository
import io.luna.net.msg.out.GameChatboxMessageWriter
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.embedded.EmbeddedChannel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageTapTest {

    private val log = MessageLog(capacity = 10) { 7 }
    private val channel = loggedInChannel()
    private val written = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() {
        written.filter { it.payload.refCnt() > 0 }.forEach { it.payload.releaseAll() }
    }

    private fun chat(text: String): GameMessage = encode(GameChatboxMessageWriter(text)).also { written += it }

    /** Bytes that leave a channel whose `game-encoder` is the real one, as they would reach the client. */
    private fun bytesOnTheWire(tapped: Boolean): String {
        val wire = EmbeddedChannel()
        wire.pipeline().addLast("game-encoder", GameMessageEncoder(IsaacCipher(intArrayOf(1, 2, 3, 4))))
        if (tapped) {
            MessageTap.attach(wire, log)
        }
        wire.writeOutbound(chat("You get some logs."))
        val bytes = wire.readOutbound<ByteBuf>()
        return ByteBufUtil.hexDump(bytes).also { bytes.release() }
    }

    @Test
    fun `a tapped channel records what is written to it`() {
        MessageTap.attach(channel, log)

        channel.writeOutbound(chat("You get some logs."))

        assertEquals(
            listOf(RecordedMessage(1, 7, "GameChatboxMessageWriter", mapOf("message" to "You get some logs."))),
            log.since(0).messages,
        )
    }

    @Test
    fun `the client receives the same bytes with the tap as without`() {
        assertEquals(bytesOnTheWire(tapped = false), bytesOnTheWire(tapped = true))
    }

    @Test
    fun `the written message itself goes on to the encoder`() {
        MessageTap.attach(channel, log)
        val message = chat("You get some logs.")

        channel.writeOutbound(message)

        assertSame(message, channel.readOutbound<GameMessage>())
    }

    @Test
    fun `other outbound objects pass through unrecorded`() {
        MessageTap.attach(channel, log)

        channel.writeOutbound("not a game message")

        assertEquals(emptyList<RecordedMessage>(), log.since(0).messages)
    }

    @Test
    fun `a message the log fails on is still sent`() {
        MessageTap.attach(channel, MessageLog(capacity = 1) { error("the clock broke") })
        val message = chat("You get some logs.")

        channel.writeOutbound(message)

        assertSame(message, channel.readOutbound<GameMessage>())
    }

    @Test
    fun `the tap sits on the tail side of the game encoder`() {
        MessageTap.attach(channel, log)

        val names = channel.pipeline().names()
        assertEquals(names.indexOf("game-encoder") + 1, names.indexOf(MessageTap.NAME))
    }

    @Test
    fun `attaching to a logged-in channel succeeds`() {
        assertTrue(MessageTap.attach(channel, log))
    }

    @Test
    fun `a channel without a game encoder is not tapped`() {
        assertFalse(MessageTap.attach(EmbeddedChannel(), log))
    }

    @Test
    fun `a channel is tapped only once`() {
        MessageTap.attach(channel, log)

        assertFalse(MessageTap.attach(channel, MessageLog(capacity = 1) { 0 }))
    }

    @Test
    fun `the bot channel has no pipeline to tap`() {
        assertFalse(MessageTap.attach(BotChannel.CHANNEL, log))
    }

    @Test
    fun `the tap of a channel is found with its log`() {
        MessageTap.attach(channel, log)

        assertSame(log, MessageTap.of(channel)?.log)
    }

    @Test
    fun `an untapped channel has no tap`() {
        assertNull(MessageTap.of(channel))
    }

    @Test
    fun `the bot channel has no tap`() {
        assertNull(MessageTap.of(BotChannel.CHANNEL))
    }

    @Test
    fun `a real client's messages are read from its tap`() {
        MessageTap.attach(channel, log)

        assertSame(log, messageLogOf(GameClient(channel, GameMessageRepository(), null)))
    }

    @Test
    fun `a client without a tap has no message log`() {
        assertNull(messageLogOf(GameClient(BotChannel.CHANNEL, GameMessageRepository(), null)))
    }
}
