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
            listOf(32017, 32018, 32019, 32021, 32022, 32023, 32027, 32028, 32029, 32030),
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
    fun `the first reflex row's face is no step slot`() {
        assertNull(BuilderWidgets.slotOf(BuilderWidgets.reflexRowFace(0)))
    }

    @Test
    fun `a reflex row's face is known by its row`() {
        assertEquals(3, BuilderWidgets.reflexRowOf(BuilderWidgets.reflexRowFace(3)))
    }

    @Test
    fun `a reflex row's other widgets and ids below the rows are no row`() {
        assertEquals(listOf(null, null), listOf(BuilderWidgets.reflexRowOf(BuilderWidgets.reflexRowText(3)), BuilderWidgets.reflexRowOf(BuilderWidgets.REFLEX_ROWS)))
    }

    @Test
    fun `the most step slots end below the reflex rows`() {
        assertEquals(BuilderWidgets.REFLEX_ROWS, BuilderWidgets.SLOT_BASE + (BuilderWidgets.MOST_STEP_SLOTS + 1) * 16)
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
            listOf(30860, 30861, 30862, 30863, 30864, 30865, 30866, 30867, 30868, 30869, 30870, 30871),
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
            listOf(30810, 30812, 31190),
            listOf(BuilderWidgets.warning(0), BuilderWidgets.warning(BuilderWidgets.WARNING_LINES - 1), BuilderWidgets.row(BuilderWidgets.ROWS)),
        )
    }

    @Test
    fun `toggle buttons mirror the client's ids`() {
        assertEquals(
            listOf(30873, 30877, 30880, 30888, 30853),
            listOf(
                BuilderWidgets.toggles(1, 2), BuilderWidgets.toggleFace(1, 2, 1), BuilderWidgets.toggles(1, 3),
                BuilderWidgets.toggleFrame(1, 3, 2), BuilderWidgets.toggleText(0, 3, 0),
            ),
        )
    }

    @Test
    fun `lists mirror the client's ids`() {
        assertEquals(
            listOf(31600, 31202, 31604, 31212, 31537, 31614, 31232),
            listOf(
                BuilderWidgets.list(1), BuilderWidgets.listAdd(0), BuilderWidgets.listAddText(1), BuilderWidgets.linePicture(0, 0),
                BuilderWidgets.lineName(0, 27), BuilderWidgets.lineAmount(1, 0), BuilderWidgets.lineRemoveFace(0, 1),
            ),
        )
    }

    @Test
    fun `a toggle button's face names its row and button`() {
        assertEquals(listOf(7 to 1, 2 to 2), listOf(BuilderWidgets.toggleOf(BuilderWidgets.toggleFace(7, 2, 1)), BuilderWidgets.toggleOf(BuilderWidgets.toggleFace(2, 3, 2))))
    }

    @Test
    fun `another widget is no toggle button`() {
        assertNull(BuilderWidgets.toggleOf(BuilderWidgets.toggleFrame(7, 2, 1)))
    }

    @Test
    fun `a list's first line names its list`() {
        assertEquals(1, BuilderWidgets.listAddOf(BuilderWidgets.listAdd(1)))
    }

    @Test
    fun `another widget is no list's first line`() {
        assertNull(BuilderWidgets.listAddOf(BuilderWidgets.listAddText(1)))
    }

    @Test
    fun `a line's remove button names its list and line`() {
        assertEquals(1 to 27, BuilderWidgets.lineRemoveOf(BuilderWidgets.lineRemoveFace(1, 27)))
    }

    @Test
    fun `another widget is no remove button`() {
        assertNull(BuilderWidgets.lineRemoveOf(BuilderWidgets.lineName(1, 27)))
    }

    @Test
    fun `a line's amount box and All button follow its layer, as the client lays them`() {
        assertEquals(
            listOf(31215, 31216, 31217, 31218),
            listOf(BuilderWidgets.lineAmountFace(0, 0), BuilderWidgets.lineAmountFrame(0, 0), BuilderWidgets.lineAmountText(0, 0), BuilderWidgets.lineAllFace(0, 0)),
        )
    }

    @Test
    fun `a line's amount box names its list and line`() {
        assertEquals(1 to 3, BuilderWidgets.lineAmountOf(BuilderWidgets.lineAmountFace(1, 3)))
    }

    @Test
    fun `a line's All button names its list and line`() {
        assertEquals(0 to 27, BuilderWidgets.lineAllOf(BuilderWidgets.lineAllFace(0, 27)))
    }

    @Test
    fun `another widget is no amount box or All button`() {
        assertEquals(listOf(null, null), listOf(BuilderWidgets.lineAmountOf(BuilderWidgets.lineAllFace(0, 0)), BuilderWidgets.lineAllOf(BuilderWidgets.lineAmountFace(0, 0))))
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
