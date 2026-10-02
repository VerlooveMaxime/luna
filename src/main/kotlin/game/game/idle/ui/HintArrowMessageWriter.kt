package game.idle.ui

import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/** Where on its tile a tile arrow points: the centre, or the middle of one edge (the client's arrow types 2 to 6). */
enum class TileEdge(val type: Int) {
    CENTRE(2),
    WEST(3),
    EAST(4),
    SOUTH(5),
    NORTH(6),
}

/**
 * The blinking yellow arrow the client draws over an npc or a tile and on the minimap (opcode [OPCODE], always 6
 * bytes). Luna's own writers can neither point at a tile edge, raise the arrow, nor remove it.
 */
class HintArrowMessageWriter private constructor(
    private val type: Int,
    private val index: Int,
    private val x: Int,
    private val y: Int,
    private val height: Int,
) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage {
        val message = ByteMessage.message(OPCODE, buffer).put(type)
        return if (type == NPC) {
            message.putShort(index).putShort(0).put(0)
        } else {
            message.putShort(x).putShort(y).put(height)
        }
    }

    companion object {
        const val OPCODE = 199
        const val NPC = 1
        const val HIDDEN = 0
        const val MAX_HEIGHT = 255

        fun overNpc(index: Int) = HintArrowMessageWriter(NPC, index, x = 0, y = 0, height = 0)

        /** [height] lifts the arrow off the floor of the viewer's plane; the packet carries no plane. */
        fun overTile(position: Position, edge: TileEdge, height: Int): HintArrowMessageWriter {
            require(height >= 0) { "A hint arrow cannot sit below the floor: $height." }
            require(height <= MAX_HEIGHT) { "A hint arrow is at most $MAX_HEIGHT high: $height." }
            return HintArrowMessageWriter(edge.type, index = 0, position.x, position.y, height)
        }

        /** The client draws nothing for a type it does not know. */
        fun hidden() = HintArrowMessageWriter(HIDDEN, index = 0, x = 0, y = 0, height = 0)
    }
}
