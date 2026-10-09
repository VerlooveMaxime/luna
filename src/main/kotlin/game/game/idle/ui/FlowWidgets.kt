package game.idle.ui

/** What a click on a saved-flow row of the Idle tab does. */
enum class SavedFlowAction { LOAD, NEW, DELETE }

/** A click on saved-flow [slot]'s [action] button. */
data class SavedFlowClick(val slot: Int, val action: SavedFlowAction)

/**
 * Ids of the Idle tab the IdleRS client defines in code (`idlers.FlowWidgets` in `luna-client`, the layout lives
 * there), in the sidebar's unused slot 7: status lines, Run and Stop, then the saved flows, the way into the builder, a
 * row per saved-flow slot the player has (packet 108) and the padlock. Both files must agree on every id.
 */
object FlowWidgets {

    const val FIRST_ID = 30000
    const val ID_LIMIT = 30300

    const val TAB = 30000
    const val TAB_STATUS_1 = 30002
    const val TAB_STATUS_2 = 30003
    const val TAB_STATUS_3 = 30004
    const val TAB_RUN = 30011
    const val TAB_STOP = 30012

    private const val ROW_BASE = 30100
    private const val ROW_STRIDE = 12

    /** The most saved-flow slots the tab's ids have room for. */
    const val MOST_SAVED_SLOTS = (ID_LIMIT - ROW_BASE) / ROW_STRIDE

    /** The room the status lines leave their text, in the plain font. */
    const val STATUS_ROOM = 170

    /** Rows shown before the saved flows scroll; a scrolling list leaves its rows less room, the scrollbar's. */
    private const val ROWS_SHOWN = 3
    private const val NAME_ROOM = 166
    private const val SCROLLBAR = 16

    private const val LOAD = 6
    private const val NEW = 8
    private const val DELETE = 10

    fun owns(widgetId: Int): Boolean = widgetId in FIRST_ID until ID_LIMIT

    /** The room a row leaves the saved flow's name, in the small font, for a player with [slots] saved-flow slots. */
    fun nameRoom(slots: Int): Int = if (slots > ROWS_SHOWN) NAME_ROOM - SCROLLBAR else NAME_ROOM

    fun row(slot: Int): Int = ROW_BASE + slot * ROW_STRIDE

    /** The layer of the row's frame, shown on the current flow's row: only layers hide. */
    fun rowFrameLayer(slot: Int): Int = row(slot) + 1

    fun rowName(slot: Int): Int = row(slot) + 3

    fun rowCount(slot: Int): Int = row(slot) + 4

    /** The layer of the row's Load, hidden on an empty slot. */
    fun rowLoadLayer(slot: Int): Int = row(slot) + 5

    fun rowLoad(slot: Int): Int = row(slot) + LOAD

    /** The layer of the row's New, shown on an empty slot only. */
    fun rowNewLayer(slot: Int): Int = row(slot) + 7

    fun rowNew(slot: Int): Int = row(slot) + NEW

    /** The layer of the row's x, hidden on an empty slot. */
    fun rowDeleteLayer(slot: Int): Int = row(slot) + 9

    fun rowDelete(slot: Int): Int = row(slot) + DELETE

    /** The saved-flow button [widgetId] is, null for any other widget. */
    fun savedFlowClick(widgetId: Int): SavedFlowClick? {
        val offset = widgetId - ROW_BASE
        if (offset < 0) return null
        val action = when (offset % ROW_STRIDE) {
            LOAD -> SavedFlowAction.LOAD
            NEW -> SavedFlowAction.NEW
            DELETE -> SavedFlowAction.DELETE
            else -> return null
        }
        return SavedFlowClick(offset / ROW_STRIDE, action)
    }
}
