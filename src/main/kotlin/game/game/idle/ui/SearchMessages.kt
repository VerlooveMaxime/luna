package game.idle.ui

import io.luna.game.event.impl.PlayerEvent
import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageReader
import io.luna.net.msg.GameMessageWriter
import io.netty.buffer.ByteBuf

/** What a chatbox prompt asks for; the client reads its ordinal. */
enum class PromptMode { SEARCH, NAME }

/**
 * Opens the IdleRS client's chatbox prompt (opcode [OPCODE], unused by the 377 protocol both ways): the prompt's
 * [serial], its [mode], its [title], the line a search shows when its list is empty, the [text] the typed line starts
 * with and the most characters it takes. A search's rows follow in a [SearchRowsMessageWriter].
 */
class SearchOpenMessageWriter(
    private val serial: Int,
    private val mode: PromptMode,
    private val title: String,
    private val emptyLine: String,
    private val text: String,
    private val mostCharacters: Int,
) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, MessageType.VAR_SHORT, buffer)
            .put(serial).put(mode.ordinal).putString(title).putString(emptyLine).putString(text).put(mostCharacters)

    companion object {
        const val OPCODE = 103
    }
}

/**
 * A page of the chatbox search's rows (opcode [OPCODE]): the prompt's [serial], the [query] it answers ("" for the
 * opening rows), how many rows match in all ([total]), the grid's [columns], the place of the first row ([offset]), a
 * count, then each row's index, flags, label, note and picture. The harness log records [rows], one
 * [SearchRow.describe] per row.
 */
class SearchRowsMessageWriter(
    private val serial: Int,
    private val query: String,
    private val total: Int,
    private val columns: Int,
    private val offset: Int,
    private val searchRows: List<SearchRow>,
) : GameMessageWriter() {

    private val count: Int = searchRows.size

    private val rows: String = describe(searchRows)

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage {
        val message = ByteMessage.message(OPCODE, MessageType.VAR_SHORT, buffer)
            .put(serial).putString(query).putShort(total).put(columns).putShort(offset).putShort(count)
        for (row in searchRows) {
            message.putShort(row.index).put(if (row.greyed) GREYED else 0).putString(row.label).putString(row.note)
            PictureEncoding.write(row.picture, message)
        }
        return message
    }

    companion object {
        const val OPCODE = 105

        /** Bit of a row's flags byte set when it is greyed. */
        const val GREYED = 1

        fun describe(rows: List<SearchRow>): String = rows.joinToString("; ", transform = SearchRow::describe)
    }
}

/** The player's client wants [count] rows from [offset] of what [query] matches in the search prompt [serial] names. */
class SearchPageEvent(player: Player, val serial: Int, val offset: Int, val count: Int, val query: String) : PlayerEvent(player)

/** The player picked row [index] of the search prompt [serial] names. */
class SearchPickEvent(player: Player, val serial: Int, val index: Int) : PlayerEvent(player)

/** The player closed the search prompt [serial] names without a pick. */
class SearchClosedEvent(player: Player, val serial: Int) : PlayerEvent(player)

/** The player pressed Enter on the name prompt [serial] names, its line [typed]. */
class SearchNameEvent(player: Player, val serial: Int, val typed: String) : PlayerEvent(player)

/**
 * Reads a page request (opcode 102, a byte length: serial byte, offset short, count byte, query string); registered
 * in `data/net/incoming_message_data.json`.
 */
class SearchPageMessageReader : GameMessageReader<SearchPageEvent>() {

    override fun decode(player: Player, msg: GameMessage): SearchPageEvent {
        val serial = msg.payload.get(false)
        val offset = msg.payload.getShort(false)
        val count = msg.payload.get(false)
        return SearchPageEvent(player, serial, offset, count, msg.payload.string)
    }
}

/** Reads a pick (opcode 103: serial byte, row index short); registered in `data/net/incoming_message_data.json`. */
class SearchPickMessageReader : GameMessageReader<SearchPickEvent>() {

    override fun decode(player: Player, msg: GameMessage): SearchPickEvent {
        val serial = msg.payload.get(false)
        val index = msg.payload.getShort(false)
        return SearchPickEvent(player, serial, index)
    }
}

/** Reads a close (opcode 105: serial byte); registered in `data/net/incoming_message_data.json`. */
class SearchClosedMessageReader : GameMessageReader<SearchClosedEvent>() {

    override fun decode(player: Player, msg: GameMessage): SearchClosedEvent = SearchClosedEvent(player, msg.payload.get(false))
}

/** Reads a name (opcode 106, a byte length: serial byte, typed string); registered in `data/net/incoming_message_data.json`. */
class SearchNameMessageReader : GameMessageReader<SearchNameEvent>() {

    override fun decode(player: Player, msg: GameMessage): SearchNameEvent {
        val serial = msg.payload.get(false)
        return SearchNameEvent(player, serial, msg.payload.string)
    }
}
