package game.idle.tutorial

import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TutorialScriptTest {

    private val data = TutorialFixtures.data
    private val script = TutorialScript(data)
    private val door = TutorialFixtures.door

    @Test
    fun `a step shows its own help box and arrow`() {
        val screen = script.screen(OPEN_HOUSE_DOOR)

        assertEquals(data.steps.getValue(OPEN_HOUSE_DOOR).help to data.steps.getValue(OPEN_HOUSE_DOOR).arrow, screen.help to screen.arrow)
    }

    @Test
    fun `side tabs that appeared at earlier steps stay`() {
        assertEquals(setOf(TabIndex.LOGOUT, TabIndex.SETTINGS, TabIndex.INVENTORY), script.screen(FIND_SURVIVAL_EXPERT).tabs)
    }

    @Test
    fun `side tabs of later steps are not shown yet`() {
        assertEquals(setOf(TabIndex.LOGOUT, TabIndex.SETTINGS), script.screen(TALK_TO_GUIDE).tabs)
    }

    @Test
    fun `closing the designer moves a new character on to the guide`() {
        assertEquals(TALK_TO_GUIDE, script.designerClosed(DESIGN_CHARACTER))
    }

    @Test
    fun `closing a window later in the tutorial moves nobody on`() {
        assertNull(script.designerClosed(TALK_TO_GUIDE))
    }

    @Test
    fun `the guide welcomes a player who has not heard him yet and sends them to the door`() {
        assertEquals(Talk(TutorialScript.GUIDE_WELCOME, advanceTo = OPEN_HOUSE_DOOR), script.talkToGuide(TALK_TO_GUIDE))
    }

    @Test
    fun `the guide only points at the door once he has welcomed the player`() {
        assertEquals(Talk(TutorialScript.GUIDE_AGAIN, advanceTo = null), script.talkToGuide(OPEN_HOUSE_DOOR))
    }

    @Test
    fun `a door stays locked before its step`() {
        assertEquals(DoorOutcome.Locked(TutorialFixtures.LOCKED), script.openDoor(door, TALK_TO_GUIDE))
    }

    @Test
    fun `going through a door at its step moves the player on`() {
        assertEquals(DoorOutcome.Pass(FIND_SURVIVAL_EXPERT), script.openDoor(door, OPEN_HOUSE_DOOR))
    }

    @Test
    fun `going through a door again later moves nobody on`() {
        assertEquals(DoorOutcome.Pass(null), script.openDoor(door, FIND_SURVIVAL_EXPERT))
    }

    @Test
    fun `data without the guide's dialogues is refused`() {
        val withoutGuide = data.copy(dialogues = data.dialogues - TutorialScript.GUIDE_AGAIN)

        assertThrows<IllegalArgumentException> { TutorialScript(withoutGuide) }
    }
}
