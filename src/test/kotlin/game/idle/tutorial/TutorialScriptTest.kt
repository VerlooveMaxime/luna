package game.idle.tutorial

import game.idle.tutorial.TutorialScript.Companion.BRONZE_AXE
import game.idle.tutorial.TutorialScript.Companion.SMALL_FISHING_NET
import game.idle.tutorial.TutorialScript.Companion.TINDERBOX
import game.idle.tutorial.TutorialStep.CATCH_SHRIMP
import game.idle.tutorial.TutorialStep.COOK_AGAIN
import game.idle.tutorial.TutorialStep.COOK_SHRIMP
import game.idle.tutorial.TutorialStep.CUT_TREE
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.DONE
import game.idle.tutorial.TutorialStep.FIND_MASTER_CHEF
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.LEAVE_SURVIVAL_AREA
import game.idle.tutorial.TutorialStep.LIGHT_FIRE
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.TALK_ABOUT_FOOD
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        assertEquals(setOf(TabIndex.LOGOUT, TabIndex.SETTINGS, TabIndex.INVENTORY), script.screen(CUT_TREE).tabs)
    }

    @Test
    fun `side tabs of later steps are not shown yet`() {
        assertEquals(setOf(TabIndex.LOGOUT, TabIndex.SETTINGS), script.screen(TALK_TO_GUIDE).tabs)
    }

    @Test
    fun `a step can flash one of its tabs`() {
        assertEquals(TabIndex.INVENTORY, script.screen(OPEN_INVENTORY).flash)
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
        assertEquals(Talk(TutorialScript.GUIDE_WELCOME, Progress(OPEN_HOUSE_DOOR)), script.talkToGuide(TALK_TO_GUIDE))
    }

    @Test
    fun `the guide only points at the door once he has welcomed the player`() {
        assertEquals(Talk(TutorialScript.GUIDE_AGAIN, progress = null), script.talkToGuide(OPEN_HOUSE_DOOR))
    }

    @Test
    fun `the Survival Expert welcomes a player who found her and sends them to their backpack`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_WELCOME, Progress(OPEN_INVENTORY)), script.talkToSurvivalExpert(FIND_SURVIVAL_EXPERT))
    }

    @Test
    fun `the Survival Expert's welcome moves nobody on before her part of the island`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_WELCOME, progress = null), script.talkToSurvivalExpert(OPEN_HOUSE_DOOR))
    }

    @Test
    fun `the Survival Expert points at the backpack until it is open`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_INVENTORY, progress = null), script.talkToSurvivalExpert(OPEN_INVENTORY))
    }

    @Test
    fun `the Survival Expert repeats how to make a fire until there is one`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_FIRE, progress = null), script.talkToSurvivalExpert(LIGHT_FIRE))
    }

    @Test
    fun `the Survival Expert points at the skills until they are open`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_SKILLS, progress = null), script.talkToSurvivalExpert(OPEN_SKILLS))
    }

    @Test
    fun `the Survival Expert hands over a net when she talks about food`() {
        val expected = Talk(TutorialScript.SURVIVAL_FOOD, Progress(CATCH_SHRIMP, listOf(SMALL_FISHING_NET)))

        assertEquals(expected, script.talkToSurvivalExpert(TALK_ABOUT_FOOD))
    }

    @Test
    fun `the Survival Expert repeats how to catch and cook a shrimp until one is cooked`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_SHRIMP, progress = null), script.talkToSurvivalExpert(COOK_AGAIN))
    }

    @Test
    fun `the Survival Expert sends a player who is done to the gate`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_DONE, progress = null), script.talkToSurvivalExpert(LEAVE_SURVIVAL_AREA))
    }

    @Test
    fun `no tools are kept for a player who has not been given them`() {
        assertEquals(emptyList<Int>(), script.tools(OPEN_INVENTORY))
    }

    @Test
    fun `the axe and tinderbox are kept from the tree on`() {
        assertEquals(listOf(BRONZE_AXE, TINDERBOX), script.tools(TALK_ABOUT_FOOD))
    }

    @Test
    fun `the net is kept too once it was handed over`() {
        assertEquals(listOf(BRONZE_AXE, TINDERBOX, SMALL_FISHING_NET), script.tools(FIND_MASTER_CHEF))
    }

    @Test
    fun `no tools are kept once the tutorial is done`() {
        assertEquals(emptyList<Int>(), script.tools(DONE))
    }

    @Test
    fun `an axe and tinderbox handed back together show in one box`() {
        assertEquals(listOf(TutorialScript.GIVES_AXE_AND_TINDERBOX), script.toolBoxes(listOf(BRONZE_AXE, TINDERBOX)))
    }

    @Test
    fun `an axe handed back alone shows in its own box`() {
        assertEquals(listOf(TutorialScript.GIVES_AXE), script.toolBoxes(listOf(BRONZE_AXE)))
    }

    @Test
    fun `a tinderbox handed back alone shows in its own box`() {
        assertEquals(listOf(TutorialScript.GIVES_TINDERBOX), script.toolBoxes(listOf(TINDERBOX)))
    }

    @Test
    fun `a net handed back shows after the other tools`() {
        val expected = listOf(TutorialScript.GIVES_TINDERBOX, TutorialScript.GIVES_NET)

        assertEquals(expected, script.toolBoxes(listOf(TINDERBOX, SMALL_FISHING_NET)))
    }

    @Test
    fun `nothing handed back shows no box`() {
        assertEquals(emptyList<String>(), script.toolBoxes(emptyList()))
    }

    @Test
    fun `each activity has its own help box to wait with`() {
        assertEquals(data.busy.getValue(TutorialScript.FISHING), script.busyHelp(TutorialScript.FISHING))
    }

    @Test
    fun `a chat line the island never sent is quiet`() {
        assertTrue(script.quiet("You get some logs."))
    }

    @Test
    fun `other chat lines are not quiet`() {
        assertFalse(script.quiet("You need an axe to chop this tree."))
    }

    @Test
    fun `data without a help box for each activity is refused`() {
        val withoutFishing = data.copy(busy = data.busy - TutorialScript.FISHING)

        assertThrows<IllegalArgumentException> { TutorialScript(withoutFishing) }
    }

    @Test
    fun `opening the flashing backpack hands over the axe and tinderbox and moves on to the tree`() {
        assertEquals(Progress(CUT_TREE, listOf(BRONZE_AXE, TINDERBOX)), script.tabOpened(OPEN_INVENTORY, TabIndex.INVENTORY))
    }

    @Test
    fun `opening the flashing skills tab moves on to the next step`() {
        assertEquals(Progress(TALK_ABOUT_FOOD), script.tabOpened(OPEN_SKILLS, TabIndex.SKILL))
    }

    @Test
    fun `opening a tab that is not flashing moves nobody on`() {
        assertNull(script.tabOpened(OPEN_INVENTORY, TabIndex.SKILL))
    }

    @Test
    fun `opening a tab after the tutorial moves nobody on`() {
        assertNull(script.tabOpened(DONE, TabIndex.INVENTORY))
    }

    @Test
    fun `the first log moves the player on to lighting a fire`() {
        assertEquals(LIGHT_FIRE, script.experienceGained(CUT_TREE, Skill.WOODCUTTING))
    }

    @Test
    fun `the first fire moves the player on to the skills`() {
        assertEquals(OPEN_SKILLS, script.experienceGained(LIGHT_FIRE, Skill.FIREMAKING))
    }

    @Test
    fun `the first shrimp moves the player on to cooking`() {
        assertEquals(COOK_SHRIMP, script.experienceGained(CATCH_SHRIMP, Skill.FISHING))
    }

    @Test
    fun `experience in another skill moves nobody on`() {
        assertNull(script.experienceGained(CUT_TREE, Skill.FIREMAKING))
    }

    @Test
    fun `experience at a step that waits for none moves nobody on`() {
        assertNull(script.experienceGained(OPEN_SKILLS, Skill.WOODCUTTING))
    }

    @Test
    fun `the first shrimp cooked always burns`() {
        assertEquals(ScriptedCook(burnt = true, advanceTo = COOK_AGAIN), script.cookShrimp(COOK_SHRIMP))
    }

    @Test
    fun `the second shrimp cooked always cooks`() {
        assertEquals(ScriptedCook(burnt = false, advanceTo = LEAVE_SURVIVAL_AREA), script.cookShrimp(COOK_AGAIN))
    }

    @Test
    fun `shrimp cooked later are left to Luna's cooking`() {
        assertNull(script.cookShrimp(LEAVE_SURVIVAL_AREA))
    }

    @Test
    fun `wielding waits for the worn equipment tab`() {
        assertFalse(script.mayWield(FIND_MASTER_CHEF))
    }

    @Test
    fun `wielding is free once the tutorial is done`() {
        assertTrue(script.mayWield(DONE))
    }

    @Test
    fun `wielding is free once a step shows the worn equipment tab`() {
        val withEquipment = data.steps.getValue(CUT_TREE).copy(tabs = listOf(TabIndex.EQUIPMENT))
        val script = TutorialScript(data.copy(steps = data.steps + (CUT_TREE to withEquipment)))

        assertTrue(script.mayWield(LIGHT_FIRE))
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
    fun `data without one of the instructors' dialogues is refused`() {
        val withoutExpert = data.copy(dialogues = data.dialogues - TutorialScript.SURVIVAL_DONE)

        assertThrows<IllegalArgumentException> { TutorialScript(withoutExpert) }
    }
}
