package game.idle.ui

import game.harness.encode
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.net.codec.MessageType
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuilderWidgetsTest {

    private val encoded = mutableListOf<GameMessage>()

    @AfterEach
    fun releasePayloads() {
        encoded.forEach { it.payload.releaseAll() }
        TestWorld.reset()
    }

    private fun encoded(writer: GameMessageWriter) = encode(writer).also { encoded += it }

    private fun bytes(message: GameMessage): List<Int> {
        val buffer = message.payload.buffer
        return (buffer.readerIndex() until buffer.writerIndex()).map { buffer.getByte(it).toInt() and 0xff }
    }

    @Test
    fun `slot widgets follow each other within their slot`() {
        assertEquals(
            listOf(31017, 31018, 31019, 31021, 31022, 31023, 31027, 31028, 31029, 31030),
            listOf(
                BuilderWidgets.slotFace(1), BuilderWidgets.slotFrame(1), BuilderWidgets.slotPicture(1), BuilderWidgets.slotCorner(1),
                BuilderWidgets.slotNumber(1), BuilderWidgets.slotKind(1), BuilderWidgets.slotLine(1, 3), BuilderWidgets.slotPlus(1),
                BuilderWidgets.slotAdd(1), BuilderWidgets.slotCornerLayer(1),
            ),
        )
    }

    @Test
    fun `a slot's face names its slot`() {
        assertEquals(5, BuilderWidgets.slotOf(BuilderWidgets.slotFace(5)))
    }

    @Test
    fun `another part of a slot names none`() {
        assertNull(BuilderWidgets.slotOf(BuilderWidgets.slotLine(5, 0)))
    }

    @Test
    fun `a fixed widget names no slot`() {
        assertNull(BuilderWidgets.slotOf(BuilderWidgets.RUN))
    }

    @Test
    fun `a kind button's face names its kind`() {
        assertEquals(15, BuilderWidgets.kindOf(BuilderWidgets.kindFace(15)))
    }

    @Test
    fun `a kind's label names no kind`() {
        assertNull(BuilderWidgets.kindOf(BuilderWidgets.kindLabel(0)))
    }

    @Test
    fun `kind buttons stay among the fixed ids`() {
        assertEquals(listOf(30736, 30737, 30738, 30739, 30799), listOf(
            BuilderWidgets.kindButton(0), BuilderWidgets.kindFace(0), BuilderWidgets.kindPicture(0), BuilderWidgets.kindLabel(0),
            BuilderWidgets.kindLabel(BuilderWidgets.KIND_BUTTONS - 1),
        ))
    }

    @Test
    fun `the builder owns its fixed ids and every slot`() {
        assertTrue(listOf(BuilderWidgets.FIRST_ID, BuilderWidgets.ID_LIMIT - 1, BuilderWidgets.slotFace(500)).all(BuilderWidgets::owns))
    }

    @Test
    fun `the builder owns nothing below its first id`() {
        assertFalse(BuilderWidgets.owns(BuilderWidgets.FIRST_ID - 1))
    }

    @Test
    fun `the fixed ids run up to the slots`() {
        assertEquals(BuilderWidgets.SLOT_BASE, BuilderWidgets.ID_LIMIT)
    }

    @Test
    fun `configure row widgets follow each other within their row`() {
        assertEquals(
            listOf(30843, 30844, 30845, 30846, 30847, 30848, 30849, 30850, 30851, 30852, 30853, 30854),
            listOf(
                BuilderWidgets.row(1), BuilderWidgets.rowLabel(1), BuilderWidgets.rowField(1), BuilderWidgets.rowFace(1),
                BuilderWidgets.rowFrame(1), BuilderWidgets.rowPicture(1), BuilderWidgets.rowText(1), BuilderWidgets.rowPlainText(1),
                BuilderWidgets.rowButton(1), BuilderWidgets.rowButtonFace(1), BuilderWidgets.rowButtonText(1), BuilderWidgets.rowNote(1),
            ),
        )
    }

    @Test
    fun `configure rows and warnings stay among the fixed ids`() {
        assertEquals(
            listOf(30810, 30812, 30960),
            listOf(BuilderWidgets.warning(0), BuilderWidgets.warning(BuilderWidgets.WARNING_LINES - 1), BuilderWidgets.row(BuilderWidgets.ROWS)),
        )
    }

    @Test
    fun `a field's face names its row`() {
        assertEquals(7, BuilderWidgets.fieldOf(BuilderWidgets.rowFace(7)))
    }

    @Test
    fun `another part of a row names no field`() {
        assertNull(BuilderWidgets.fieldOf(BuilderWidgets.rowText(7)))
    }

    @Test
    fun `a row button's face names its row`() {
        assertEquals(9, BuilderWidgets.buttonOf(BuilderWidgets.rowButtonFace(9)))
    }

    @Test
    fun `a field's face names no button`() {
        assertNull(BuilderWidgets.buttonOf(BuilderWidgets.rowFace(9)))
    }

    @Test
    fun `a colour keeps the top five bits of each channel`() {
        assertEquals(0x585040, WidgetColourMessageWriter.unpacked(WidgetColourMessageWriter.packed(0x5c5243)))
    }

    @Test
    fun `a colour packs red, green and blue from the top`() {
        assertEquals(0b11111_00000_00001, WidgetColourMessageWriter.packed(0xff0008))
    }

    @Test
    fun `the colour packet is the 377's own, fixed`() {
        val message = encoded(WidgetColourMessageWriter(31018, 0x00ff00))

        assertEquals(listOf<Any>(218, MessageType.FIXED), listOf(message.opcode, message.type))
    }

    @Test
    fun `the colour packet carries the widget, then the colour with its low byte added`() {
        assertEquals(listOf(0x79, 0x2a, 0x03, 0xe0 + 128 and 0xff), bytes(encoded(WidgetColourMessageWriter(31018, 0x00ff00))))
    }

    @Test
    fun `the slot count goes in the IdleRS packet under its sub-opcode`() {
        val message = encoded(SlotCountMessageWriter(SlotCountMessageWriter.STEP_SLOTS, 6))

        assertEquals(listOf<Any>(108, MessageType.VAR, listOf(0, 0, 6)), listOf(message.opcode, message.type, bytes(message)))
    }

    @Test
    fun `updates go out as the client's widget packets`() {
        val player = TestWorld.login("updates", Position(3200, 3200))

        WidgetUpdate.send(
            player,
            listOf(
                WidgetUpdate.Text(30712, "Stopped."),
                WidgetUpdate.Picture(31019, WidgetPicture.Item(1521)),
                WidgetUpdate.Visible(30710, visible = false),
                WidgetUpdate.Colour(31018, 0x00ff00),
            ),
        )

        assertEquals(
            listOf("WidgetTextMessageWriter", "PictureMessageWriter", "WidgetVisibilityMessageWriter", "WidgetColourMessageWriter"),
            TestWorld.messages(player).takeLast(4).map { it.type },
        )
    }

    @Test
    fun `a hidden widget is sent hidden`() {
        val player = TestWorld.login("updates", Position(3200, 3200))

        WidgetUpdate.send(player, listOf(WidgetUpdate.Visible(30710, visible = false)))

        assertEquals(true, TestWorld.messages(player).last().fields["hiddenUntilHovered"])
    }
}
