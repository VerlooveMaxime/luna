package game.idle.ui

import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteMessage
import io.luna.net.codec.MessageType
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter
import io.netty.buffer.ByteBuf

/**
 * Ids of the flow builder's screens the IdleRS client defines in code (`idlers.BuilderWidgets` in `luna-client`, the
 * layout lives there): one root with a layer per screen, the overview's step slots from [SLOT_BASE], as many as the
 * player has, then the padlock; the configure screen's rows from [ROW_BASE]. Both files must agree on every id.
 */
object BuilderWidgets {

    const val FIRST_ID = 30700
    const val ID_LIMIT = 31000

    const val ROOT = 30700
    const val CLOSE = 30704

    const val OVERVIEW = 30710
    /** The layer holding the slots, which a dragged slot's move names. */
    const val SLOTS = 30711
    const val STATUS = 30712
    const val BASE_LEVELS = 30714
    const val BOOSTED_LEVELS = 30715
    const val BASE_LEVELS_FRAME = 30716
    const val BOOSTED_LEVELS_FRAME = 30717
    const val RUN = 30718
    const val STOP = 30719
    const val CLEAR = 30720

    const val KINDS = 30730
    const val KINDS_TITLE = 30731
    const val KINDS_BACK = 30732
    const val KIND_BUTTONS = 16
    private const val KIND_BASE = 30736
    private const val KIND_STRIDE = 4

    const val CONFIGURE = 30800
    const val HEADER_PICTURE = 30801
    /** The layer of the header's corner box and picture, which hides as one. */
    const val HEADER_CORNER_LAYER = 30802
    const val HEADER_CORNER = 30804
    const val HEADER_NAME = 30805
    const val HEADER_DESCRIPTION = 30806
    const val WARNING_LINES = 3
    private const val WARNING_BASE = 30810
    const val DELETE = 30813
    const val BACK = 30814
    const val SAVE = 30815

    /** The configure screen's rows: five a column, the left column's first. */
    const val ROWS_PER_COLUMN = 5
    const val ROWS = 2 * ROWS_PER_COLUMN
    private const val ROW_BASE = 30830
    private const val ROW_STRIDE = 13

    /** Room for a field's text, a note and a warning line, as the client lays them out. */
    const val FIELD_ROOM = 104
    const val NOTE_ROOM = 160
    const val WARNING_ROOM = 492
    const val DESCRIPTION_ROOM = 450

    const val SLOT_BASE = 31000
    private const val SLOT_STRIDE = 16
    const val SLOT_LINES = 4

    /** Room for a line of a slot, as the client lays it out. */
    const val LINE_ROOM = 108

    /** Frame colours: the tile's edge, the running step, a step that cannot work, a free slot (no edge). */
    const val EDGE = 0x5c5243
    const val RUNNING = 0x00ff00
    const val PROBLEM = 0xff3030
    const val FREE = 0x3b342a

    /** A lit toggle's frame, and an unlit one's, which is the panel's colour: a box cannot be hidden. */
    const val LIT = 0xff981f
    const val UNLIT = 0x332d25

    /** A configure field's frame, and the one being typed (Maxime, 2026-10-09: the mockup's yellow). */
    const val FIELD_EDGE = 0x5c5243
    const val TYPING = 0xffff00

    /** The layer of a kind button: the client hides only layers (packet 82 is read by its layer drawing alone). */
    fun kindButton(kind: Int): Int = KIND_BASE + kind * KIND_STRIDE

    fun kindFace(kind: Int): Int = kindButton(kind) + 1

    fun kindPicture(kind: Int): Int = kindButton(kind) + 2

    fun kindLabel(kind: Int): Int = kindButton(kind) + 3

    private fun slot(slot: Int): Int = SLOT_BASE + slot * SLOT_STRIDE

    fun slotFace(slot: Int): Int = slot(slot) + 1

    fun slotFrame(slot: Int): Int = slot(slot) + 2

    fun slotPicture(slot: Int): Int = slot(slot) + 3

    /** The layer of the corner's box and picture, which hides as one (only layers hide). */
    fun slotCornerLayer(slot: Int): Int = slot(slot) + 14

    fun slotCorner(slot: Int): Int = slot(slot) + 5

    fun slotNumber(slot: Int): Int = slot(slot) + 6

    fun slotKind(slot: Int): Int = slot(slot) + 7

    fun slotLine(slot: Int, line: Int): Int = slot(slot) + 8 + line

    fun slotPlus(slot: Int): Int = slot(slot) + 12

    fun slotAdd(slot: Int): Int = slot(slot) + 13

    fun warning(line: Int): Int = WARNING_BASE + line

    /** The layer of configure row [row], hidden when the step has no setting for it. */
    fun row(row: Int): Int = ROW_BASE + row * ROW_STRIDE

    fun rowLabel(row: Int): Int = row(row) + 1

    /** The layer of the row's field box, hidden on a note row. */
    fun rowField(row: Int): Int = row(row) + 2

    fun rowFace(row: Int): Int = row(row) + 3

    fun rowFrame(row: Int): Int = row(row) + 4

    fun rowPicture(row: Int): Int = row(row) + 5

    /** The field's text right of its picture, for a search. */
    fun rowText(row: Int): Int = row(row) + 6

    /** The field's text without a picture, for a typed number or a tile. */
    fun rowPlainText(row: Int): Int = row(row) + 7

    /** The layer of the button right of the field (the amount's "Full", "All", "No end"). */
    fun rowButton(row: Int): Int = row(row) + 8

    fun rowButtonFace(row: Int): Int = row(row) + 9

    fun rowButtonText(row: Int): Int = row(row) + 10

    fun rowNote(row: Int): Int = row(row) + 11

    /** The configure row whose field face [widgetId] is, or null. */
    fun fieldOf(widgetId: Int): Int? = (0 until ROWS).firstOrNull { rowFace(it) == widgetId }

    /** The configure row whose button face [widgetId] is, or null. */
    fun buttonOf(widgetId: Int): Int? = (0 until ROWS).firstOrNull { rowButtonFace(it) == widgetId }

    /** The fixed ids run up to the slots, so every id from [FIRST_ID] on is the builder's. */
    fun owns(widgetId: Int): Boolean = widgetId >= FIRST_ID

    /** The slot whose face [widgetId] is, or null. */
    fun slotOf(widgetId: Int): Int? =
        ((widgetId - SLOT_BASE) / SLOT_STRIDE).takeIf { widgetId >= SLOT_BASE && slotFace(it) == widgetId }

    /** The kind button whose face [widgetId] is, or null. */
    fun kindOf(widgetId: Int): Int? = (0 until KIND_BUTTONS).firstOrNull { kindFace(it) == widgetId }
}

/** A change the server makes to a widget the client defines in code. */
sealed interface WidgetUpdate {

    data class Text(val id: Int, val text: String) : WidgetUpdate

    data class Picture(val id: Int, val picture: WidgetPicture) : WidgetUpdate

    data class Visible(val id: Int, val visible: Boolean) : WidgetUpdate

    data class Colour(val id: Int, val rgb: Int) : WidgetUpdate

    companion object {
        /**
         * Sends [updates] to [player]. Every one goes out every time, as `IdleUi.sendTexts` explains: the client
         * rebuilds our widgets blank each time the window closes.
         */
        fun send(player: Player, updates: List<WidgetUpdate>) =
            updates.forEach { update ->
                val writer = when (update) {
                    is Text -> WidgetTextMessageWriter(update.text, update.id)
                    is Picture -> PictureMessageWriter(update.id, update.picture)
                    is Visible -> WidgetVisibilityMessageWriter(update.id, !update.visible)
                    is Colour -> WidgetColourMessageWriter(update.id, update.rgb)
                }
                player.queue(writer)
            }
    }
}

/**
 * The 377's own widget colour packet (opcode [OPCODE]), which Luna has no writer for: the widget's id and its colour as
 * 15-bit RGB, the client keeping the top five bits of each channel. The harness log shows [rgb], the 24-bit colour the
 * client ends up with.
 */
class WidgetColourMessageWriter(private val widgetId: Int, colour: Int) : GameMessageWriter() {

    private val rgb: Int = unpacked(packed(colour))

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, buffer).putShort(widgetId).putShort(packed(rgb), ValueType.ADD)

    companion object {
        const val OPCODE = 218

        fun packed(rgb: Int): Int = (rgb shr 19 and 0x1f shl 10) or (rgb shr 11 and 0x1f shl 5) or (rgb shr 3 and 0x1f)

        /** What the client makes of a packed colour. */
        fun unpacked(packed: Int): Int = (packed shr 10 and 0x1f shl 19) or (packed shr 5 and 0x1f shl 11) or (packed and 0x1f shl 3)
    }
}

/**
 * The IdleRS packet (opcode [OPCODE], the last the 377 protocol leaves free from the server): a sub-opcode byte, then
 * its content, so more can follow under one opcode. [BUILDER_SLOTS]: how many step slots the builder shows, which the
 * client builds its slot widgets for.
 */
class BuilderSlotsMessageWriter(private val slots: Int) : GameMessageWriter() {

    override fun write(player: Player?, buffer: ByteBuf): ByteMessage =
        ByteMessage.message(OPCODE, MessageType.VAR, buffer).put(BUILDER_SLOTS).putShort(slots)

    companion object {
        const val OPCODE = 108
        const val BUILDER_SLOTS = 0
    }
}
