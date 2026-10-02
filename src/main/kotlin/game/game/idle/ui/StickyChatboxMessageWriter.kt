package game.idle.ui

import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.ByteOrder
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/**
 * Shows interface [id] in the chatbox slot that walking, dialogues and closing windows leave alone (opcode [OPCODE]),
 * where the tutorial help box lives; [NONE] empties it. Luna has no writer for this slot.
 */
class StickyChatboxMessageWriter(private val id: Int) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, buffer).putShort(id, ByteOrder.LITTLE)

    companion object {
        const val OPCODE = 158
        const val NONE = -1
    }
}
