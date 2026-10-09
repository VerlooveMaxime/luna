package game.harness

import game.idle.ui.HintArrowMessageWriter
import game.idle.ui.MapPickMessageWriter
import game.idle.ui.PictureEncoding
import game.idle.ui.PictureMessageWriter
import game.idle.ui.PromptMode
import game.idle.ui.SearchOpenMessageWriter
import game.idle.ui.SearchRow
import game.idle.ui.SearchRowsMessageWriter
import game.idle.ui.StatusOverlayMessageWriter
import game.idle.ui.StickyChatboxMessageWriter
import game.idle.ui.WidgetPicture
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessage
import java.nio.charset.StandardCharsets

/** A sent message reduced to what the log keeps: the writer's type name and its plain fields. */
data class DecodedMessage(val type: String, val fields: Map<String, Any>)

/**
 * Reads an encoded [GameMessage] back into the type and fields [MessageLog] records from a writer, for real clients,
 * whose writers the harness never sees. Only the messages agents read results from are decoded; any other opcode is
 * kept as `opcode <n>` with its size. Each layout mirrors the `write` method of the writer it is named after.
 */
object EncodedMessageDecoder {

    private class Layout(val type: String, val read: (Payload) -> Map<String, Any>)

    private val layouts: Map<Int, Layout> = mapOf(
        63 to Layout("GameChatboxMessageWriter") { mapOf("message" to it.string()) },
        232 to Layout("WidgetTextMessageWriter") {
            val id = it.short(ByteOrder.LITTLE, ValueType.ADD)
            mapOf("text" to it.string(), "id" to id)
        },
        159 to Layout("InterfaceMessageWriter") { mapOf("id" to it.short(ByteOrder.LITTLE, ValueType.ADD)) },
        109 to Layout("DialogueInterfaceMessageWriter") { mapOf("id" to it.short()) },
        50 to Layout("WalkableInterfaceMessageWriter") { mapOf("id" to it.short()) },
        StatusOverlayMessageWriter.OPCODE to Layout("StatusOverlayMessageWriter") { mapOf("text" to it.string()) },
        MapPickMessageWriter.OPCODE to Layout("MapPickMessageWriter") { mapOf("x" to it.short(), "y" to it.short()) },
        PictureMessageWriter.OPCODE to Layout("PictureMessageWriter", ::picture),
        SearchOpenMessageWriter.OPCODE to Layout("SearchOpenMessageWriter") {
            mapOf(
                "serial" to it.byte(),
                "mode" to PromptMode.entries[it.byte()].name,
                "title" to it.string(),
                "emptyLine" to it.string(),
                "text" to it.string(),
                "mostCharacters" to it.byte(),
            )
        },
        SearchRowsMessageWriter.OPCODE to Layout("SearchRowsMessageWriter", ::searchRows),
        StickyChatboxMessageWriter.OPCODE to Layout("StickyChatboxMessageWriter") {
            mapOf("id" to it.short(ByteOrder.LITTLE))
        },
        HintArrowMessageWriter.OPCODE to Layout("HintArrowMessageWriter", ::hintArrow),
        238 to Layout("FlashTabMessageWriter") { mapOf("tab" to TabIndex.forIndex(it.byte()).name) },
        128 to Layout("InventoryOverlayMessageWriter") {
            val interfaceId = it.short(transform = ValueType.ADD)
            mapOf("interfaceId" to interfaceId, "overlayInterfaceId" to it.short(ByteOrder.LITTLE, ValueType.ADD))
        },
        29 to Layout("CloseWindowsMessageWriter") { emptyMap() },
        58 to Layout("NumberInputMessageWriter") { emptyMap() },
        6 to Layout("TextInputMessageWriter") { emptyMap() },
        5 to Layout("LogoutMessageWriter") { emptyMap() },
    )

    /** Player updating and NPC updating, skipped like their writers are. */
    private val updatingOpcodes = setOf(90, 71)

    /**
     * `null` for player and NPC updating. Reads a duplicate of the payload, so the message is sent exactly as it
     * would have been. Throws [IndexOutOfBoundsException] when a payload is shorter than its layout.
     */
    fun decode(message: GameMessage): DecodedMessage? {
        if (message.opcode in updatingOpcodes) {
            return null
        }
        val layout = layouts[message.opcode] ?: return undecoded(message)
        return DecodedMessage(layout.type, layout.read(Payload(ByteMessage.wrap(message.payload.buffer.duplicate()))))
    }

    /** An npc arrow carries an index where a tile arrow carries x, y and height; the writer holds all four. */
    private fun hintArrow(payload: Payload): Map<String, Any> {
        val type = payload.byte()
        val first = payload.short()
        val second = payload.short()
        val third = payload.byte()
        return if (type == HintArrowMessageWriter.NPC) {
            mapOf("type" to type, "index" to first, "x" to 0, "y" to 0, "height" to 0)
        } else {
            mapOf("type" to type, "index" to 0, "x" to first, "y" to second, "height" to third)
        }
    }

    private fun picture(payload: Payload): Map<String, Any> {
        val widgetId = payload.short()
        val picture = readPicture(payload)
        val name = (picture as? WidgetPicture.Media)?.name.orEmpty()
        return mapOf("widgetId" to widgetId, "source" to PictureEncoding.source(picture), "name" to name, "id" to PictureEncoding.id(picture))
    }

    /** A sprite carries its name and a byte index, an item or npc a short id, nothing nothing. */
    private fun readPicture(payload: Payload): WidgetPicture =
        when (payload.byte()) {
            PictureEncoding.NONE -> WidgetPicture.None
            PictureEncoding.MEDIA -> WidgetPicture.Media(payload.string(), payload.byte())
            PictureEncoding.ITEM -> WidgetPicture.Item(payload.short())
            else -> WidgetPicture.NpcBody(payload.short())
        }

    private fun searchRows(payload: Payload): Map<String, Any> {
        val header = mapOf(
            "serial" to payload.byte(),
            "query" to payload.string(),
            "total" to payload.short(),
            "columns" to payload.byte(),
            "offset" to payload.short(),
        )
        val rows = List(payload.short()) {
            val index = payload.short()
            val greyed = payload.byte() and SearchRowsMessageWriter.GREYED != 0
            SearchRow(index, payload.string(), payload.string(), greyed, readPicture(payload))
        }
        return header + mapOf("count" to rows.size, "rows" to SearchRowsMessageWriter.describe(rows))
    }

    private fun undecoded(message: GameMessage) =
        DecodedMessage("opcode ${message.opcode}", mapOf("opcode" to message.opcode, "size" to message.size))

    /** Reads values back as the writers' `int` and `String` fields held them, where `ByteMessage` alone would not. */
    private class Payload(private val message: ByteMessage) {

        fun byte(): Int = message.get(false)

        /** `ByteMessage.getShort` never sign-extends, whatever its `signed` flag, and writers send ids like -1. */
        fun short(order: ByteOrder = ByteOrder.BIG, transform: ValueType = ValueType.NORMAL): Int =
            message.getShort(order, transform).toShort().toInt()

        /** `ByteMessage.getString` sign-extends bytes above 127; writers encode strings as ISO-8859-1. */
        fun string(): String {
            val buffer = message.buffer
            val length = buffer.bytesBefore(STRING_TERMINATOR)
            if (length < 0) {
                throw IndexOutOfBoundsException("string without its terminator at index ${buffer.readerIndex()}")
            }
            val text = buffer.readCharSequence(length, StandardCharsets.ISO_8859_1).toString()
            buffer.skipBytes(1)
            return text
        }

        private companion object {
            const val STRING_TERMINATOR: Byte = 10
        }
    }
}
