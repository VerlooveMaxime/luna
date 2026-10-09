package game.idle.ui

import org.junit.jupiter.api.Assertions.assertFalse
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
}
