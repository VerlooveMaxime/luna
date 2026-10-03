package game.idle.ui

import game.idle.location.Tile
import io.luna.game.event.impl.PlayerEvent
import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageReader
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/**
 * Asks the IdleRS client to open its world map over the flow builder, centred on tile [x], [y], for the player to
 * pick a tile; the client answers with a [MapPickEvent], or nothing when the player closes the map. Opcode [OPCODE]
 * is unused by the 377 protocol in both directions. Plain fields, so the harness log records them.
 */
class MapPickMessageWriter(private val x: Int, private val y: Int) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, MessageType.FIXED, buffer).putShort(x).putShort(y)

    companion object {
        const val OPCODE = 101
    }
}

/** The ground-floor tile a player picked on the IdleRS world map. */
class MapPickEvent(player: Player, val tile: Tile) : PlayerEvent(player)

/** Reads the client's world map pick (opcode 101, two unsigned shorts); registered in `data/net/incoming_message_data.json`. */
class MapPickMessageReader : GameMessageReader<MapPickEvent>() {

    override fun decode(player: Player, msg: GameMessage): MapPickEvent {
        val x = msg.payload.getShort(false)
        val y = msg.payload.getShort(false)
        return MapPickEvent(player, Tile(x, y))
    }
}
