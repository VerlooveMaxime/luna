package game.idle.ui

/**
 * Ids of the Idle tab the IdleRS client defines in code (`idlers.FlowWidgets` in `luna-client`, the layout lives
 * there), in the sidebar's unused slot 7: status lines, the button that opens the flow builder, Run and Stop. Both
 * files must agree on every id.
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

    fun owns(widgetId: Int): Boolean = widgetId in FIRST_ID until ID_LIMIT
}
