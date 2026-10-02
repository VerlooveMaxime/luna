package game.harness

import game.idle.ui.StatusOverlayMessageWriter
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

    private fun undecoded(message: GameMessage) =
        DecodedMessage("opcode ${message.opcode}", mapOf("opcode" to message.opcode, "size" to message.size))

    /** Reads values back as the writers' `int` and `String` fields held them, where `ByteMessage` alone would not. */
    private class Payload(private val message: ByteMessage) {

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
