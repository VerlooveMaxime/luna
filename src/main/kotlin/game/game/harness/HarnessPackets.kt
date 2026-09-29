package game.harness

import io.luna.net.codec.ByteMessage
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.MessageType
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessage
import io.luna.util.StringUtils

/**
 * Hand-encoded client packets, byte for byte what the 377 client sends for a menu click.
 *
 * Opcodes follow the client's menu code (option n is definition action n - 1); byte layouts mirror the decoders in
 * `io.luna.net.msg.in`. `BotOutputMessageHandler` is deliberately not reused: its object option 3 layout and its
 * NPC option numbering disagree with those decoders and with the client.
 */
object HarnessPackets {

    const val INVENTORY_INTERFACE = 3214

    /** Option number to opcode, for the options Luna has a decoder for. */
    val OBJECT_OPCODES = mapOf(1 to 181, 2 to 241, 3 to 50)
    val NPC_OPCODES = mapOf(1 to 112, 2 to 67, 3 to 13, 4 to 42, 5 to 8)
    val ITEM_OPCODES = mapOf(1 to 203, 2 to 24, 3 to 161, 4 to 228, 5 to 4)
    val GROUND_ITEM_OPCODES = mapOf(3 to 71, 4 to 54)

    fun objectClick(option: Int, x: Int, y: Int, id: Int): GameMessage {
        val opcode = opcodeFor(OBJECT_OPCODES, option, "object")
        val payload = ByteMessage.raw()
        when (opcode) {
            181 -> payload.putShort(x, ValueType.ADD).putShort(y, ByteOrder.LITTLE).putShort(id, ByteOrder.LITTLE)
            241 -> payload.putShort(id).putShort(x).putShort(y, ValueType.ADD)
            else -> payload.putShort(y, ValueType.ADD).putShort(id, ByteOrder.LITTLE)
                .putShort(x, ByteOrder.LITTLE, ValueType.ADD)
        }
        return GameMessage(opcode, MessageType.FIXED, payload)
    }

    fun npcClick(option: Int, index: Int): GameMessage {
        val opcode = opcodeFor(NPC_OPCODES, option, "NPC")
        val payload = ByteMessage.raw()
        when (opcode) {
            67 -> payload.putShort(index, ValueType.ADD)
            13 -> payload.putShort(index, ByteOrder.LITTLE, ValueType.ADD)
            else -> payload.putShort(index, ByteOrder.LITTLE)
        }
        return GameMessage(opcode, MessageType.FIXED, payload)
    }

    /** A click on an inventory item; option 2 is decoded as equip and option 5 as drop. */
    fun itemClick(option: Int, slot: Int, id: Int): GameMessage {
        val opcode = opcodeFor(ITEM_OPCODES, option, "item")
        val payload = ByteMessage.raw()
        when (opcode) {
            203 -> payload.putShort(INVENTORY_INTERFACE, ValueType.ADD).putShort(slot, ByteOrder.LITTLE)
                .putShort(id, ByteOrder.LITTLE)
            24 -> payload.putShort(INVENTORY_INTERFACE, ByteOrder.LITTLE).putShort(id, ByteOrder.LITTLE)
                .putShort(slot, ValueType.ADD)
            161 -> payload.putShort(id, ValueType.ADD).putShort(slot, ByteOrder.LITTLE, ValueType.ADD)
                .putShort(INVENTORY_INTERFACE, ByteOrder.LITTLE, ValueType.ADD)
            228 -> payload.putShort(slot, ByteOrder.LITTLE).putShort(id, ValueType.ADD).putShort(INVENTORY_INTERFACE)
            else -> payload.putShort(slot, ByteOrder.LITTLE).putShort(id, ByteOrder.LITTLE, ValueType.ADD)
                .putShort(INVENTORY_INTERFACE, ByteOrder.LITTLE, ValueType.ADD)
        }
        return GameMessage(opcode, MessageType.FIXED, payload)
    }

    fun groundItemClick(option: Int, x: Int, y: Int, id: Int): GameMessage {
        val opcode = opcodeFor(GROUND_ITEM_OPCODES, option, "ground item")
        val payload = ByteMessage.raw()
        when (opcode) {
            71 -> payload.putShort(id, ByteOrder.LITTLE, ValueType.ADD).putShort(x, ByteOrder.LITTLE, ValueType.ADD)
                .putShort(y, ValueType.ADD)
            else -> payload.putShort(id, ValueType.ADD).putShort(y, ByteOrder.LITTLE).putShort(x)
        }
        return GameMessage(opcode, MessageType.FIXED, payload)
    }

    /** A `::command`; the leading colons are optional, as the client strips them too. */
    fun command(text: String): GameMessage {
        val command = text.trim().removePrefix("::").trim()
        if (command.isEmpty()) {
            throw HarnessException(400, "the command is empty")
        }
        return GameMessage(56, MessageType.VAR, ByteMessage.raw().putString(command))
    }

    /** Public chat in yellow with no effect, packed the way the client compresses chat text. */
    fun chat(text: String): GameMessage {
        if (text.isBlank()) {
            throw HarnessException(400, "the chat text is empty")
        }
        val packed = ByteMessage.raw()
        try {
            StringUtils.packText(text, packed)
            val payload = ByteMessage.raw().put(0, ValueType.NEGATE).put(0, ValueType.ADD).putBytes(packed)
            return GameMessage(49, MessageType.VAR, payload)
        } finally {
            packed.releaseAll()
        }
    }

    fun button(id: Int): GameMessage = GameMessage(79, MessageType.FIXED, ByteMessage.raw().putShort(id))

    fun continueDialogue(): GameMessage = GameMessage(226, MessageType.FIXED, ByteMessage.raw().putShort(0))

    fun closeInterface(): GameMessage = GameMessage(110, MessageType.FIXED, ByteMessage.raw())

    private fun opcodeFor(opcodes: Map<Int, Int>, option: Int, target: String): Int =
        opcodes[option] ?: throw HarnessException(
            400,
            "$target option $option is not handled by the server; handled options are ${opcodes.keys.joinToString()}",
        )
}
