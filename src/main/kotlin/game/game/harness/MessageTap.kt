package game.harness

import io.luna.net.msg.GameMessage
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOutboundHandlerAdapter
import io.netty.channel.ChannelPromise
import org.apache.logging.log4j.LogManager

/**
 * Records each [GameMessage] written to a real client's channel into [log], then passes it on untouched.
 *
 * It sits on the tail side of `game-encoder`, so it sees messages before they are encrypted and released.
 */
class MessageTap(val log: MessageLog) : ChannelOutboundHandlerAdapter() {

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        if (msg is GameMessage) {
            recordWithoutFailing(msg)
        }
        ctx.write(msg, promise)
    }

    /**
     * An exception thrown from an outbound handler reaches Luna's upstream handler, which closes the connection.
     * A recording bug must cost the log an entry, not the player their session.
     */
    private fun recordWithoutFailing(message: GameMessage) {
        try {
            log.record(message)
        } catch (e: RuntimeException) {
            logger.error("Could not record opcode {} for the harness; it was sent anyway.", message.opcode, e)
        }
    }

    companion object {
        const val NAME = "harness-tap"

        /** Named by upstream `LoginClient` when it swaps the login codec for the game one. */
        private const val ENCODER = "game-encoder"

        private val logger = LogManager.getLogger(MessageTap::class.java)

        /**
         * Taps [channel] into [log]. False when there is nothing to tap (bots and headless players have no game
         * encoder) or a tap is already there.
         */
        fun attach(channel: Channel, log: MessageLog): Boolean {
            val pipeline = channel.pipeline() ?: return false
            if (pipeline.get(ENCODER) == null || pipeline.get(NAME) != null) {
                return false
            }
            pipeline.addAfter(ENCODER, NAME, MessageTap(log))
            return true
        }

        fun of(channel: Channel): MessageTap? = channel.pipeline()?.get(NAME) as? MessageTap
    }
}
