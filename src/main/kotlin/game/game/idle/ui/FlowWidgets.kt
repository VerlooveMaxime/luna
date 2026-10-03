package game.idle.ui

/** What a click on an IdleRS widget asks for. */
sealed interface BuilderAction {
    data object OpenBuilder : BuilderAction
    data object Close : BuilderAction
    data class EditRow(val row: Int) : BuilderAction
    data class MoveUp(val row: Int) : BuilderAction
    data class MoveDown(val row: Int) : BuilderAction
    data class Delete(val row: Int) : BuilderAction
    data object CycleKind : BuilderAction
    data class CycleField(val index: Int) : BuilderAction
    data object Add : BuilderAction
    data object NewStep : BuilderAction
    data object Run : BuilderAction
    data object Stop : BuilderAction
    data object Clear : BuilderAction
}

/**
 * Ids of the widgets the IdleRS client defines in code (`idlers.FlowWidgets` in `luna-client`, the layout lives
 * there): the sidebar tab in the unused slot 7 and the flow builder screen. Both files must agree on every id.
 */
object FlowWidgets {

    const val FIRST_ID = 30000
    const val ID_LIMIT = 30300

    const val TAB = 30000
    const val TAB_STATUS_1 = 30002
    const val TAB_STATUS_2 = 30003
    const val TAB_STATUS_3 = 30004
    const val TAB_OPEN_BUILDER = 30010
    const val TAB_RUN = 30011
    const val TAB_STOP = 30012

    const val BUILDER = 30100
    const val BUILDER_CLOSE = 30102

    const val ROWS = 8
    const val ROW_BASE = 30110
    const val ROW_STRIDE = 10

    const val DRAFT_LABEL = 30200
    const val DRAFT_KIND = 30201
    const val DRAFT_ADD = 30207
    const val DRAFT_NEW = 30208

    const val STATUS = 30210
    const val RUN = 30211
    const val STOP = 30212
    const val CLEAR = 30213
    const val MESSAGE = 30214

    /** The draft step's field buttons and the labels above them, as many as a kind of step may have fields. */
    val DRAFT_FIELDS = listOf(30202, 30203, 30204)
    val DRAFT_FIELD_LABELS = listOf(30221, 30222, 30223)

    fun rowText(row: Int): Int = ROW_BASE + row * ROW_STRIDE

    fun rowUp(row: Int): Int = rowText(row) + 1

    fun rowDown(row: Int): Int = rowText(row) + 2

    fun rowDelete(row: Int): Int = rowText(row) + 3

    fun owns(widgetId: Int): Boolean = widgetId in FIRST_ID until ID_LIMIT

    /** Null for a widget that does nothing when clicked. */
    fun action(widgetId: Int): BuilderAction? =
        when (widgetId) {
            TAB_OPEN_BUILDER -> BuilderAction.OpenBuilder
            BUILDER_CLOSE -> BuilderAction.Close
            TAB_RUN, RUN -> BuilderAction.Run
            TAB_STOP, STOP -> BuilderAction.Stop
            CLEAR -> BuilderAction.Clear
            DRAFT_KIND -> BuilderAction.CycleKind
            in DRAFT_FIELDS -> BuilderAction.CycleField(DRAFT_FIELDS.indexOf(widgetId))
            DRAFT_ADD -> BuilderAction.Add
            DRAFT_NEW -> BuilderAction.NewStep
            in ROW_BASE until ROW_BASE + ROWS * ROW_STRIDE -> rowAction(widgetId)
            else -> null
        }

    private fun rowAction(widgetId: Int): BuilderAction? {
        val row = (widgetId - ROW_BASE) / ROW_STRIDE
        return when (widgetId - rowText(row)) {
            0 -> BuilderAction.EditRow(row)
            1 -> BuilderAction.MoveUp(row)
            2 -> BuilderAction.MoveDown(row)
            3 -> BuilderAction.Delete(row)
            else -> null
        }
    }
}
