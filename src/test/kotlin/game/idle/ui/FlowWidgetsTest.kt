package game.idle.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowWidgetsTest {

    @Test
    fun `the Idle tab's range is owned, its neighbours are not`() {
        assertTrue(FlowWidgets.owns(FlowWidgets.FIRST_ID))
        assertTrue(FlowWidgets.owns(FlowWidgets.ID_LIMIT - 1))
        assertFalse(FlowWidgets.owns(FlowWidgets.FIRST_ID - 1))
        assertFalse(FlowWidgets.owns(FlowWidgets.ID_LIMIT))
    }

    @Test
    fun `the tab has ids for sixteen saved-flow slots`() {
        assertEquals(16, FlowWidgets.MOST_SAVED_SLOTS)
    }

    @Test
    fun `the last saved-flow row ends inside the tab's range`() {
        assertTrue(FlowWidgets.rowDelete(FlowWidgets.MOST_SAVED_SLOTS - 1) < FlowWidgets.ID_LIMIT)
    }

    @Test
    fun `a row's Load is a load of its slot`() {
        assertEquals(SavedFlowClick(1, SavedFlowAction.LOAD), FlowWidgets.savedFlowClick(FlowWidgets.rowLoad(1)))
    }

    @Test
    fun `a row's New starts a flow in its slot`() {
        assertEquals(SavedFlowClick(0, SavedFlowAction.NEW), FlowWidgets.savedFlowClick(FlowWidgets.rowNew(0)))
    }

    @Test
    fun `a row's x empties its slot`() {
        assertEquals(SavedFlowClick(2, SavedFlowAction.DELETE), FlowWidgets.savedFlowClick(FlowWidgets.rowDelete(2)))
    }

    @Test
    fun `a row's name is no button`() {
        assertNull(FlowWidgets.savedFlowClick(FlowWidgets.rowName(0)))
    }

    @Test
    fun `a widget before the rows is no saved-flow button`() {
        assertNull(FlowWidgets.savedFlowClick(FlowWidgets.TAB_RUN))
    }

    @Test
    fun `a list that fits leaves its rows' names the whole row`() {
        assertEquals(166, FlowWidgets.nameRoom(3))
    }

    @Test
    fun `a list that scrolls leaves its names room for the scrollbar`() {
        assertEquals(150, FlowWidgets.nameRoom(4))
    }
}
