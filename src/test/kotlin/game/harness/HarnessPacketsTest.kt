package game.harness

import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ChatEvent
import io.luna.game.event.impl.CommandEvent
import io.luna.game.event.impl.ContinueDialogueEvent
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemFourthClickEvent
import io.luna.game.event.impl.ItemClickEvent.ItemThirdClickEvent
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.MessageType
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.`in`.ButtonClickMessageReader
import io.luna.net.msg.`in`.ChatMessageReader
import io.luna.net.msg.`in`.CommandMessageReader
import io.luna.net.msg.`in`.ContinueDialogueMessageReader
import io.luna.net.msg.`in`.DropItemMessageReader
import io.luna.net.msg.`in`.EquipItemMessageReader
import io.luna.net.msg.`in`.ItemClickMessageReader
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Packets whose decoder only reads the message are decoded by the real Luna reader (with no player). Object, NPC and
 * ground item decoders look their target up in the world first, so those packets are read back field by field in
 * the decoder's order, which must be kept in step with `ObjectClickMessageReader`, `NpcClickMessageReader` and
 * `GroundItemClickMessageReader`.
 */
class HarnessPacketsTest {

    private data class Decoded(val first: Int, val second: Int, val third: Int)

    private fun readShorts(message: GameMessage, vararg layout: Pair<ByteOrder, ValueType>): List<Int> =
        layout.map { (order, type) -> message.payload.getShort(false, order, type) }

    private val big = ByteOrder.BIG to ValueType.NORMAL
    private val bigAdd = ByteOrder.BIG to ValueType.ADD
    private val little = ByteOrder.LITTLE to ValueType.NORMAL
    private val littleAdd = ByteOrder.LITTLE to ValueType.ADD

    private fun itemEvent(message: GameMessage): ItemClickEvent = ItemClickMessageReader().decode(null, message)

    @Test
    fun `object option 1 is opcode 181 with x, y and id`() {
        val message = HarnessPackets.objectClick(option = 1, x = 3171, y = 3444, id = 1278)

        assertEquals(181, message.opcode)
        assertEquals(listOf(3171, 3444, 1278), readShorts(message, bigAdd, little, little))
    }

    @Test
    fun `object option 2 is opcode 241 with id, x and y`() {
        val message = HarnessPackets.objectClick(option = 2, x = 3186, y = 3436, id = 2213)

        assertEquals(241, message.opcode)
        assertEquals(listOf(2213, 3186, 3436), readShorts(message, big, big, bigAdd))
    }

    @Test
    fun `object option 3 is opcode 50 with y, id and x`() {
        val message = HarnessPackets.objectClick(option = 3, x = 3186, y = 3436, id = 2213)

        assertEquals(50, message.opcode)
        assertEquals(listOf(3436, 2213, 3186), readShorts(message, bigAdd, little, littleAdd))
    }

    @Test
    fun `object option 4 is refused because no decoder handles it`() {
        val thrown = assertThrows<HarnessException> { HarnessPackets.objectClick(option = 4, x = 0, y = 0, id = 1) }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `npc option 1 is opcode 112 with a little-endian index`() {
        val message = HarnessPackets.npcClick(option = 1, index = 8840)

        assertEquals(112, message.opcode)
        assertEquals(listOf(8840), readShorts(message, little))
    }

    @Test
    fun `npc option 2 is the attack opcode 67`() {
        val message = HarnessPackets.npcClick(option = 2, index = 8840)

        assertEquals(67, message.opcode)
        assertEquals(listOf(8840), readShorts(message, bigAdd))
    }

    @Test
    fun `npc option 3 is opcode 13`() {
        val message = HarnessPackets.npcClick(option = 3, index = 8840)

        assertEquals(13, message.opcode)
        assertEquals(listOf(8840), readShorts(message, littleAdd))
    }

    @Test
    fun `npc option 4 is opcode 42`() {
        val message = HarnessPackets.npcClick(option = 4, index = 8840)

        assertEquals(42, message.opcode)
        assertEquals(listOf(8840), readShorts(message, little))
    }

    @Test
    fun `npc option 5 is opcode 8`() {
        val message = HarnessPackets.npcClick(option = 5, index = 8840)

        assertEquals(8, message.opcode)
        assertEquals(listOf(8840), readShorts(message, little))
    }

    @Test
    fun `npc option 6 is refused`() {
        assertThrows<HarnessException> { HarnessPackets.npcClick(option = 6, index = 1) }
    }

    @Test
    fun `item option 1 decodes as a first click on the inventory`() {
        val event = itemEvent(HarnessPackets.itemClick(option = 1, slot = 8, id = 1351))

        assertInstanceOf(ItemFirstClickEvent::class.java, event)
        assertEquals(Decoded(1351, 8, 3214), Decoded(event.id, event.index, event.interfaceId))
    }

    @Test
    fun `item option 2 decodes as equipping from the inventory`() {
        val message = HarnessPackets.itemClick(option = 2, slot = 7, id = 841)

        val event: EquipItemEvent = EquipItemMessageReader().decode(null, message)

        assertEquals(Decoded(841, 7, 3214), Decoded(event.itemId, event.index, event.interfaceId))
    }

    @Test
    fun `item option 3 decodes as a third click on the inventory`() {
        val event = itemEvent(HarnessPackets.itemClick(option = 3, slot = 2, id = 555))

        assertInstanceOf(ItemThirdClickEvent::class.java, event)
        assertEquals(Decoded(555, 2, 3214), Decoded(event.id, event.index, event.interfaceId))
    }

    @Test
    fun `item option 4 decodes as a fourth click on the inventory`() {
        val event = itemEvent(HarnessPackets.itemClick(option = 4, slot = 3, id = 554))

        assertInstanceOf(ItemFourthClickEvent::class.java, event)
        assertEquals(Decoded(554, 3, 3214), Decoded(event.id, event.index, event.interfaceId))
    }

    @Test
    fun `item option 5 decodes as dropping from the inventory`() {
        val message = HarnessPackets.itemClick(option = 5, slot = 9, id = 1511)

        val event: DropItemEvent = DropItemMessageReader().decode(null, message)

        assertEquals(Decoded(1511, 9, 3214), Decoded(event.itemId, event.index, event.widgetId))
    }

    @Test
    fun `item option 6 is refused`() {
        assertThrows<HarnessException> { HarnessPackets.itemClick(option = 6, slot = 0, id = 1) }
    }

    @Test
    fun `ground item option 3 is the take opcode 71 with id, x and y`() {
        val message = HarnessPackets.groundItemClick(option = 3, x = 3172, y = 3443, id = 1511)

        assertEquals(71, message.opcode)
        assertEquals(listOf(1511, 3172, 3443), readShorts(message, littleAdd, littleAdd, bigAdd))
    }

    @Test
    fun `ground item option 4 is opcode 54 with id, y and x`() {
        val message = HarnessPackets.groundItemClick(option = 4, x = 3172, y = 3443, id = 1511)

        assertEquals(54, message.opcode)
        assertEquals(listOf(1511, 3443, 3172), readShorts(message, bigAdd, little, big))
    }

    @Test
    fun `ground item option 1 is refused`() {
        assertThrows<HarnessException> { HarnessPackets.groundItemClick(option = 1, x = 0, y = 0, id = 1) }
    }

    @Test
    fun `command decodes with its arguments`() {
        val event: CommandEvent = CommandMessageReader().decode(null, HarnessPackets.command("item 1351 1"))

        assertEquals("item", event.name)
        assertArrayEquals(arrayOf("1351", "1"), event.args)
    }

    @Test
    fun `command strips the leading colons`() {
        val event: CommandEvent = CommandMessageReader().decode(null, HarnessPackets.command(" ::move"))

        assertEquals("move", event.name)
    }

    @Test
    fun `an empty command is refused`() {
        val thrown = assertThrows<HarnessException> { HarnessPackets.command("::") }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `chat decodes to the same text in yellow without effect`() {
        val event: ChatEvent = ChatMessageReader().decode(null, HarnessPackets.chat("hello from the harness"))

        assertEquals(Triple("hello from the harness", 0, 0), Triple(event.unpackedMessage, event.color, event.effect))
    }

    @Test
    fun `blank chat is refused`() {
        val thrown = assertThrows<HarnessException> { HarnessPackets.chat("  ") }

        assertEquals(400, thrown.status)
    }

    @Test
    fun `button decodes with its id`() {
        val event: ButtonClickEvent = ButtonClickMessageReader().decode(null, HarnessPackets.button(2482))

        assertEquals(2482, event.id)
    }

    @Test
    fun `continue dialogue decodes`() {
        val message = HarnessPackets.continueDialogue()

        val event: ContinueDialogueEvent = ContinueDialogueMessageReader().decode(null, message)

        assertEquals(0, event.widgetId)
    }

    @Test
    fun `close interface is an empty fixed opcode 110 message`() {
        val message = HarnessPackets.closeInterface()

        assertEquals(Triple(110, MessageType.FIXED, 0), Triple(message.opcode, message.type, message.size))
    }
}
