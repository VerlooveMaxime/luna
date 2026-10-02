package game.idle.ui

import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/**
 * Text the IdleRS client draws over the game view (`StatusOverlay` in the client): lines separated by
 * [LINE_SEPARATOR], an empty text clears it. Opcode [OPCODE] is unused by the 377 protocol.
 */
class StatusOverlayMessageWriter(private val text: String) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, MessageType.VAR_SHORT, buffer).putString(text)

    companion object {
        const val OPCODE = 100

        /** A newline ends a packet string, so lines are joined with this instead. */
        const val LINE_SEPARATOR = '|'
    }
}
