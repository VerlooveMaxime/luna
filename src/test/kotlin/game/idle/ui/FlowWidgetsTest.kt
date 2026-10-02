package game.idle.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowWidgetsTest {

    @Test
    fun `the reserved range is owned, its neighbours are not`() {
        assertTrue(FlowWidgets.owns(FlowWidgets.FIRST_ID))
        assertTrue(FlowWidgets.owns(FlowWidgets.ID_LIMIT - 1))
        assertFalse(FlowWidgets.owns(FlowWidgets.FIRST_ID - 1))
        assertFalse(FlowWidgets.owns(FlowWidgets.ID_LIMIT))
    }

    @Test
    fun `tab and builder buttons map to their actions`() {
        assertEquals(BuilderAction.OpenBuilder, FlowWidgets.action(FlowWidgets.TAB_OPEN_BUILDER))
        assertEquals(BuilderAction.Close, FlowWidgets.action(FlowWidgets.BUILDER_CLOSE))
        assertEquals(BuilderAction.Run, FlowWidgets.action(FlowWidgets.TAB_RUN))
        assertEquals(BuilderAction.Run, FlowWidgets.action(FlowWidgets.RUN))
        assertEquals(BuilderAction.Stop, FlowWidgets.action(FlowWidgets.TAB_STOP))
        assertEquals(BuilderAction.Stop, FlowWidgets.action(FlowWidgets.STOP))
        assertEquals(BuilderAction.Clear, FlowWidgets.action(FlowWidgets.CLEAR))
    }

    @Test
    fun `draft fields map to their actions`() {
        assertEquals(BuilderAction.CycleKind, FlowWidgets.action(FlowWidgets.DRAFT_KIND))
        assertEquals(BuilderAction.CycleResource, FlowWidgets.action(FlowWidgets.DRAFT_RESOURCE))
        assertEquals(BuilderAction.CycleLocation, FlowWidgets.action(FlowWidgets.DRAFT_LOCATION))
        assertEquals(BuilderAction.Add, FlowWidgets.action(FlowWidgets.DRAFT_ADD))
        assertEquals(BuilderAction.NewStep, FlowWidgets.action(FlowWidgets.DRAFT_NEW))
    }

    @Test
    fun `row buttons carry their row`() {
        assertEquals(BuilderAction.EditRow(2), FlowWidgets.action(FlowWidgets.rowText(2)))
        assertEquals(BuilderAction.MoveUp(0), FlowWidgets.action(FlowWidgets.rowUp(0)))
        assertEquals(BuilderAction.MoveDown(3), FlowWidgets.action(FlowWidgets.rowDown(3)))
        assertEquals(BuilderAction.Delete(7), FlowWidgets.action(FlowWidgets.rowDelete(7)))
    }

    @Test
    fun `a row's unused slots do nothing`() {
        assertNull(FlowWidgets.action(FlowWidgets.rowText(2) + 4))
    }

    @Test
    fun `labels and unknown ids do nothing`() {
        assertNull(FlowWidgets.action(FlowWidgets.STATUS))
        assertNull(FlowWidgets.action(FlowWidgets.DRAFT_LABEL))
        assertNull(FlowWidgets.action(FlowWidgets.TAB_STATUS_1))
        assertNull(FlowWidgets.action(1))
    }
}
