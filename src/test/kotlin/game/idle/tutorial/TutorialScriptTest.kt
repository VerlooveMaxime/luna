package game.idle.tutorial

import game.idle.tutorial.TutorialScript.Companion.BRONZE_AXE
import game.idle.tutorial.TutorialScript.Companion.SMALL_FISHING_NET
import game.idle.tutorial.TutorialScript.Companion.TINDERBOX
import game.idle.tutorial.TutorialStep.CATCH_SHRIMP
import game.idle.tutorial.TutorialStep.BUILD_FIRST_FLOW
import game.idle.tutorial.TutorialStep.COOK_AGAIN
import game.idle.tutorial.TutorialStep.COOK_SHRIMP
import game.idle.tutorial.TutorialStep.CUT_TREE
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.DONE
import game.idle.tutorial.TutorialStep.EXTEND_THE_FLOW
import game.idle.tutorial.TutorialStep.FIND_MASTER_CHEF
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.LEAVE_SURVIVAL_AREA
import game.idle.tutorial.TutorialStep.LIGHT_FIRE
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.OPEN_IDLE_TAB
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.STOP_FULL_LOOP
import game.idle.tutorial.TutorialStep.STOP_THE_AUTOPILOT
import game.idle.tutorial.TutorialStep.TALK_ABOUT_AUTOPILOT
import game.idle.tutorial.TutorialStep.TALK_ABOUT_FOOD
import game.idle.tutorial.TutorialStep.TALK_ABOUT_LOOP
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.player.Animations
import game.skill.cooking.cookFood.Cooking
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
    fun `the Survival Expert introduces the autopilot and sends the player to the Idle tab`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_AUTOPILOT, Progress(OPEN_IDLE_TAB)), script.talkToSurvivalExpert(TALK_ABOUT_AUTOPILOT))
    }

    @Test
    fun `the Survival Expert points at the autopilot until its lesson is done`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_AUTOPILOT_AGAIN, progress = null), script.talkToSurvivalExpert(OPEN_IDLE_TAB))
        assertEquals(Talk(TutorialScript.SURVIVAL_AUTOPILOT_AGAIN, progress = null), script.talkToSurvivalExpert(STOP_THE_AUTOPILOT))
    }

    @Test
    fun `the Survival Expert shows one flow for everything after the meal`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_LOOP, Progress(EXTEND_THE_FLOW)), script.talkToSurvivalExpert(TALK_ABOUT_LOOP))
    }

    @Test
    fun `the Survival Expert repeats the four-step flow until it was stopped`() {
        assertEquals(Talk(TutorialScript.SURVIVAL_LOOP_AGAIN, progress = null), script.talkToSurvivalExpert(EXTEND_THE_FLOW))
        assertEquals(Talk(TutorialScript.SURVIVAL_LOOP_AGAIN, progress = null), script.talkToSurvivalExpert(STOP_FULL_LOOP))
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
        assertEquals(Progress(TALK_ABOUT_AUTOPILOT), script.tabOpened(OPEN_SKILLS, TabIndex.SKILL))
    }

    @Test
    fun `opening the flashing Idle tab moves on to the first flow`() {
        assertEquals(Progress(BUILD_FIRST_FLOW), script.tabOpened(OPEN_IDLE_TAB, TabIndex.UNUSED))
    }

    private fun flow(running: Boolean, vararg steps: StepSummary, laps: Int = 0) =
        PlayerProgress(FlowProgress(running, steps.toList(), laps))

    @Test
    fun `a lesson moves on once the autopilot reaches its goal`() {
        val progress = flow(running = true, StepSummary("chop", 1), StepSummary("light", 1))

        assertEquals(TutorialStep.WATCH_THE_AUTOPILOT, script.goalProgress(BUILD_FIRST_FLOW, progress))
    }

    @Test
    fun `a lesson waits while the autopilot is short of its goal`() {
        assertNull(script.goalProgress(BUILD_FIRST_FLOW, flow(running = true, StepSummary("chop", 1), laps = 3)))
    }

    @Test
    fun `stopping the autopilot ends the lesson that asks for it`() {
        assertEquals(TALK_ABOUT_FOOD, script.goalProgress(STOP_THE_AUTOPILOT, flow(running = false)))
    }

    @Test
    fun `carrying the dough moves the player on to baking it`() {
        val progress = flow(running = false).copy(carried = setOf(TutorialScript.BREAD_DOUGH))

        assertEquals(TutorialStep.BAKE_BREAD, script.goalProgress(TutorialStep.MAKE_DOUGH, progress))
    }

    @Test
    fun `turning run on moves the player on to the next guide`() {
        assertEquals(TutorialStep.FIND_QUEST_GUIDE, script.goalProgress(TutorialStep.TURN_RUN_ON, flow(running = false).copy(runOn = true)))
    }

    @Test
    fun `a step without a goal is no lesson`() {
        assertNull(script.goalProgress(TutorialStep.WATCH_THE_AUTOPILOT, flow(running = true, laps = 9)))
        assertNull(script.goalProgress(DONE, flow(running = false)))
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
    fun `the first shrimp cooked on a fire always burns`() {
        val cook = ScriptedCook(317, Cooking.FIRES, 323, 0.0, TutorialScript.SHRIMP_BURNT, Animations.FIRE_COOKING.id, COOK_AGAIN)

        assertEquals(cook, script.scriptedCook(COOK_SHRIMP))
    }

    @Test
    fun `the second shrimp cooked on a fire always cooks`() {
        val cook = ScriptedCook(317, Cooking.FIRES, 315, 30.0, TutorialScript.SHRIMP_COOKED, Animations.FIRE_COOKING.id, TALK_ABOUT_LOOP)

        assertEquals(cook, script.scriptedCook(COOK_AGAIN))
    }

    @Test
    fun `the first bread baked on a range always bakes, quietly`() {
        val cook = ScriptedCook(2307, Cooking.RANGES, 2309, 40.0, null, Animations.RANGE_COOKING.id, TutorialStep.TALK_ABOUT_SUPPLIES)

        assertEquals(cook, script.scriptedCook(TutorialStep.BAKE_BREAD))
    }

    @Test
    fun `food cooked at other steps is left to Luna's cooking`() {
        assertNull(script.scriptedCook(LEAVE_SURVIVAL_AREA))
    }

    @Test
    fun `the quest guide welcomes a player who came through his door and asks for the quest journal`() {
        assertEquals(
            Talk(TutorialScript.QUEST_GUIDE_WELCOME, Progress(TutorialStep.OPEN_QUEST_JOURNAL)),
            script.talkToQuestGuide(TutorialStep.TALK_TO_QUEST_GUIDE),
        )
    }

    @Test
    fun `the quest guide's welcome moves nobody on before his part of the island`() {
        assertEquals(Talk(TutorialScript.QUEST_GUIDE_WELCOME, progress = null), script.talkToQuestGuide(TutorialStep.FIND_QUEST_GUIDE))
    }

    @Test
    fun `the quest guide waits for the journal to be opened`() {
        assertEquals(Talk(TutorialScript.QUEST_GUIDE_OPEN_JOURNAL, progress = null), script.talkToQuestGuide(TutorialStep.OPEN_QUEST_JOURNAL))
    }

    @Test
    fun `with the journal open the quest guide explains quests and stages and sends the player to the caves`() {
        val explained = listOf(
            TutorialScript.QUEST_GUIDE_JOURNAL,
            TutorialScript.QUEST_GUIDE_QUESTS,
            TutorialScript.QUEST_GUIDE_STAGES,
            TutorialScript.QUEST_GUIDE_CAVES,
        )

        assertEquals(Talk(explained, Progress(TutorialStep.ENTER_MINE)), script.talkToQuestGuide(TutorialStep.TALK_ABOUT_QUESTS))
    }

    @Test
    fun `later the quest guide explains quests and stages again`() {
        assertEquals(Talk(TutorialScript.QUEST_GUIDE_EXPLAINS, progress = null), script.talkToQuestGuide(TutorialStep.ENTER_MINE))
    }

    @Test
    fun `the island's journal line is yellow while the player is on it`() {
        assertEquals("@yel@Tutorial Island", script.journalLine(TutorialStep.TALK_ABOUT_QUESTS))
    }

    @Test
    fun `the island's journal line is green once it is done`() {
        assertEquals("@gre@Tutorial Island", script.journalLine(DONE))
    }

    @Test
    fun `Dezzick welcomes a player who came down the ladder and sends them prospecting`() {
        assertEquals(
            Talk(TutorialScript.DEZZICK_WELCOME, Progress(TutorialStep.PROSPECT_ROCKS)),
            script.talkToMiningInstructor(TutorialStep.TALK_TO_MINING_INSTRUCTOR),
        )
    }

    @Test
    fun `Dezzick's welcome moves nobody on before his part of the island`() {
        assertEquals(Talk(TutorialScript.DEZZICK_WELCOME, progress = null), script.talkToMiningInstructor(TutorialStep.ENTER_MINE))
    }

    @Test
    fun `while the rocks are prospected Dezzick says to prospect them`() {
        assertEquals(Talk(TutorialScript.DEZZICK_PROSPECT_AGAIN, progress = null), script.talkToMiningInstructor(TutorialStep.PROSPECTED_TIN))
    }

    @Test
    fun `with both rocks prospected Dezzick hands over a pickaxe and sends the player mining`() {
        assertEquals(
            Talk(TutorialScript.DEZZICK_PROSPECTED, Progress(TutorialStep.MINE_ORE, listOf(TutorialScript.BRONZE_PICKAXE))),
            script.talkToMiningInstructor(TutorialStep.PROSPECTED_COPPER_LAST),
        )
    }

    @Test
    fun `while the ores are mined Dezzick says to mine them`() {
        assertEquals(Talk(TutorialScript.DEZZICK_MINING, progress = null), script.talkToMiningInstructor(TutorialStep.MINED_TIN))
    }

    @Test
    fun `with both ores Dezzick points at the furnace`() {
        assertEquals(Talk(TutorialScript.DEZZICK_SMELTING, progress = null), script.talkToMiningInstructor(TutorialStep.SMELT_BAR))
    }

    @Test
    fun `with a bar Dezzick hands over a hammer and sends the player to the anvil`() {
        assertEquals(
            Talk(TutorialScript.DEZZICK_SMITHING, Progress(TutorialStep.SMITH_DAGGER, listOf(TutorialScript.HAMMER))),
            script.talkToMiningInstructor(TutorialStep.TALK_ABOUT_SMITHING),
        )
    }

    @Test
    fun `while the dagger is smithed Dezzick points at the anvil`() {
        assertEquals(Talk(TutorialScript.DEZZICK_DAGGER, progress = null), script.talkToMiningInstructor(TutorialStep.SMITH_DAGGER))
    }

    @Test
    fun `with a dagger Dezzick explains the chain and sends the player to build it`() {
        assertEquals(Talk(TutorialScript.DEZZICK_CHAIN, Progress(TutorialStep.BUILD_CHAIN)), script.talkToMiningInstructor(TutorialStep.TALK_ABOUT_CHAIN))
    }

    @Test
    fun `Dezzick repeats the chain until it is stopped, then greets`() {
        assertEquals(Talk(TutorialScript.DEZZICK_CHAIN_AGAIN, progress = null), script.talkToMiningInstructor(TutorialStep.STOP_CHAIN))
        assertEquals(Talk(TutorialScript.DEZZICK_HELLO, progress = null), script.talkToMiningInstructor(TutorialStep.LEAVE_MINE))
    }

    @Test
    fun `Dezzick keeps no tools before handing over the pickaxe, nor off the island`() {
        assertEquals(emptyList<Int>(), script.miningTools(TutorialStep.PROSPECTED_TIN_LAST))
        assertEquals(emptyList<Int>(), script.miningTools(DONE))
    }

    @Test
    fun `Dezzick keeps the pickaxe from mining on, and the hammer from smithing on`() {
        assertEquals(listOf(TutorialScript.BRONZE_PICKAXE), script.miningTools(TutorialStep.TALK_ABOUT_SMITHING))
        assertEquals(listOf(TutorialScript.BRONZE_PICKAXE, TutorialScript.HAMMER), script.miningTools(TutorialStep.SMITH_DAGGER))
    }

    @Test
    fun `each tool Dezzick hands back shows in its own box`() {
        assertEquals(
            listOf(TutorialScript.DEZZICK_GIVES_PICKAXE, TutorialScript.DEZZICK_GIVES_HAMMER),
            script.miningToolBoxes(listOf(TutorialScript.BRONZE_PICKAXE, TutorialScript.HAMMER)),
        )
        assertEquals(listOf(TutorialScript.DEZZICK_GIVES_HAMMER), script.miningToolBoxes(listOf(TutorialScript.HAMMER)))
        assertEquals(emptyList<String>(), script.miningToolBoxes(emptyList()))
    }

    @Test
    fun `either rock can be prospected first`() {
        assertEquals(TutorialStep.PROSPECTED_COPPER, script.prospected(TutorialStep.PROSPECT_ROCKS, TutorialScript.COPPER_ROCK))
        assertEquals(TutorialStep.PROSPECTED_TIN, script.prospected(TutorialStep.PROSPECT_ROCKS, TutorialScript.TIN_ROCK))
    }

    @Test
    fun `prospecting the other rock second sends the player to Dezzick`() {
        assertEquals(TutorialStep.PROSPECTED_COPPER_LAST, script.prospected(TutorialStep.PROSPECTED_TIN, TutorialScript.COPPER_ROCK))
        assertEquals(TutorialStep.PROSPECTED_TIN_LAST, script.prospected(TutorialStep.PROSPECTED_COPPER, TutorialScript.TIN_ROCK))
    }

    @Test
    fun `prospecting a rock again, or at another step, moves nobody on`() {
        assertNull(script.prospected(TutorialStep.PROSPECTED_COPPER, TutorialScript.COPPER_ROCK))
        assertNull(script.prospected(TutorialStep.MINE_ORE, TutorialScript.TIN_ROCK))
    }

    @Test
    fun `a prospected rock names its ore`() {
        assertEquals(TutorialScript.PROSPECT_COPPER, script.prospectResult(TutorialScript.COPPER_ROCK))
        assertEquals(TutorialScript.PROSPECT_TIN, script.prospectResult(TutorialScript.TIN_ROCK))
    }

    @Test
    fun `either ore can be mined first`() {
        assertEquals(TutorialStep.MINED_COPPER, script.oreProgress(TutorialStep.MINE_ORE, setOf(TutorialScript.COPPER_ORE)))
        assertEquals(TutorialStep.MINED_TIN, script.oreProgress(TutorialStep.MINE_ORE, setOf(TutorialScript.TIN_ORE)))
    }

    @Test
    fun `with both ores the player goes on to smelting, whichever came first`() {
        val both = setOf(TutorialScript.COPPER_ORE, TutorialScript.TIN_ORE)

        assertEquals(TutorialStep.SMELT_BAR, script.oreProgress(TutorialStep.MINE_ORE, both))
        assertEquals(TutorialStep.SMELT_BAR, script.oreProgress(TutorialStep.MINED_COPPER, both))
        assertEquals(TutorialStep.SMELT_BAR, script.oreProgress(TutorialStep.MINED_TIN, both))
    }

    @Test
    fun `without the missing ore nobody moves on`() {
        assertNull(script.oreProgress(TutorialStep.MINE_ORE, emptySet()))
        assertNull(script.oreProgress(TutorialStep.MINED_COPPER, setOf(TutorialScript.COPPER_ORE)))
        assertNull(script.oreProgress(TutorialStep.MINED_TIN, setOf(TutorialScript.TIN_ORE)))
    }

    @Test
    fun `ore carried at another step moves nobody on`() {
        assertNull(script.oreProgress(TutorialStep.SMELT_BAR, setOf(TutorialScript.COPPER_ORE, TutorialScript.TIN_ORE)))
    }

    @Test
    fun `mining before it is taught is answered with a box`() {
        assertEquals(TutorialScript.MINE_NOT_READY, script.objectClicked(TutorialStep.PROSPECT_ROCKS, TutorialScript.TIN_ROCK, firstOption = true))
        assertNull(script.objectClicked(TutorialStep.MINE_ORE, TutorialScript.TIN_ROCK, firstOption = true))
    }

    @Test
    fun `the furnace's own option only explains it`() {
        assertEquals(TutorialScript.FURNACE_NOT_YET, script.objectClicked(TutorialStep.MINE_ORE, TutorialScript.FURNACE, firstOption = true))
        assertEquals(TutorialScript.FURNACE_HOW, script.objectClicked(TutorialStep.SMELT_BAR, TutorialScript.FURNACE, firstOption = true))
    }

    @Test
    fun `other options and other objects are left alone`() {
        assertNull(script.objectClicked(TutorialStep.PROSPECT_ROCKS, TutorialScript.TIN_ROCK, firstOption = false))
        assertNull(script.objectClicked(TutorialStep.PROSPECT_ROCKS, TutorialScript.ANVIL, firstOption = true))
    }

    @Test
    fun `an item on the furnace before smelting is taught is answered with a box`() {
        assertEquals(TutorialScript.FURNACE_NOT_YET, script.itemUsedOn(TutorialStep.MINED_TIN, TutorialScript.FURNACE))
        assertNull(script.itemUsedOn(TutorialStep.SMELT_BAR, TutorialScript.FURNACE))
    }

    @Test
    fun `an item on an anvil before smithing is taught is answered with a box`() {
        assertEquals(TutorialScript.ANVIL_NOT_YET, script.itemUsedOn(TutorialStep.MINED_TIN, TutorialScript.ANVIL))
        assertEquals(TutorialScript.ANVIL_NO_HAMMER, script.itemUsedOn(TutorialStep.TALK_ABOUT_SMITHING, TutorialScript.ANVIL))
        assertNull(script.itemUsedOn(TutorialStep.SMITH_DAGGER, TutorialScript.ANVIL))
    }

    @Test
    fun `items on other objects are left alone`() {
        assertNull(script.itemUsedOn(TutorialStep.MINED_TIN, TutorialScript.COPPER_ROCK))
    }

    @Test
    fun `on the island only the bronze dagger is smithed`() {
        assertTrue(script.maySmith(TutorialStep.SMITH_DAGGER, TutorialScript.BRONZE_DAGGER))
        assertFalse(script.maySmith(TutorialStep.SMITH_DAGGER, 1351))
    }

    @Test
    fun `off the island anything is smithed`() {
        assertTrue(script.maySmith(DONE, 1351))
    }

    @Test
    fun `a name is said with each word capitalised`() {
        assertEquals("Tut1", script.spokenName("tut1"))
        assertEquals("Big Bob", script.spokenName("big_bob"))
        assertEquals("Big Bob", script.spokenName("big__bob"))
    }

    @Test
    fun `the chef welcomes a player who found him and hands over flour and water`() {
        assertEquals(Talk(TutorialScript.CHEF_WELCOME, Progress(TutorialStep.MAKE_DOUGH, listOf(1929, 1933))), script.talkToChef(TutorialStep.TALK_TO_CHEF))
    }

    @Test
    fun `the chef's welcome moves nobody on before his part of the island`() {
        assertEquals(Talk(TutorialScript.CHEF_WELCOME, progress = null), script.talkToChef(TutorialStep.FIND_MASTER_CHEF))
    }

    @Test
    fun `the chef talks bread while the player learns to bake`() {
        assertEquals(Talk(TutorialScript.CHEF_BREAD, progress = null), script.talkToChef(TutorialStep.BAKE_BREAD))
    }

    @Test
    fun `the chef hands over supplies for baking on autopilot`() {
        val supplies = listOf(1933, 1933, 1933, 1933, 1929, 1929, 1929, 1929)

        assertEquals(Talk(TutorialScript.CHEF_SUPPLIES, Progress(TutorialStep.BAKE_ON_AUTOPILOT, supplies)), script.talkToChef(TutorialStep.TALK_ABOUT_SUPPLIES))
    }

    @Test
    fun `the chef repeats the baking flow until it is stopped, then greets`() {
        assertEquals(Talk(TutorialScript.CHEF_SUPPLIES_AGAIN, progress = null), script.talkToChef(TutorialStep.STOP_THE_BAKING))
        assertEquals(Talk(TutorialScript.CHEF_HELLO, progress = null), script.talkToChef(TutorialStep.OPEN_MUSIC))
    }

    @Test
    fun `the chef hands back what is missing of the flour and water while the player learns to bake`() {
        assertEquals(listOf(1929, 1933), script.ingredients(TutorialStep.MAKE_DOUGH, carried = emptySet()))
        assertEquals(listOf(1933), script.ingredients(TutorialStep.BAKE_BREAD, carried = setOf(1929)))
    }

    @Test
    fun `no ingredients are handed back once there is dough, or outside the bread lesson`() {
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.BAKE_BREAD, carried = setOf(2307)))
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.TALK_TO_CHEF, carried = emptySet()))
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.TALK_ABOUT_SUPPLIES, carried = emptySet()))
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.STOP_THE_BAKING, carried = emptySet()))
    }

    @Test
    fun `a player out of flour and dough while baking on autopilot gets a new batch of supplies`() {
        assertEquals(TutorialScript.SUPPLIES, script.ingredients(TutorialStep.BAKE_ON_AUTOPILOT, carried = setOf(1929)))
        assertEquals(TutorialScript.SUPPLIES, script.ingredients(TutorialStep.WATCH_THE_BAKING, carried = setOf(2309)))
    }

    @Test
    fun `a player with flour or dough left to bake gets no new batch`() {
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.WATCH_THE_BAKING, carried = setOf(1933)))
        assertEquals(emptyList<Int>(), script.ingredients(TutorialStep.WATCH_THE_BAKING, carried = setOf(2307)))
    }

    @Test
    fun `ingredients handed back show in one box`() {
        assertEquals(listOf(TutorialScript.CHEF_GIVES_SUPPLIES), script.ingredientBoxes(TutorialScript.SUPPLIES))
        assertEquals(listOf(TutorialScript.CHEF_GIVES_FLOUR_AND_WATER), script.ingredientBoxes(listOf(1929, 1933)))
        assertEquals(listOf(TutorialScript.CHEF_GIVES_FLOUR), script.ingredientBoxes(listOf(1933)))
        assertEquals(listOf(TutorialScript.CHEF_GIVES_WATER), script.ingredientBoxes(listOf(1929)))
        assertEquals(emptyList<String>(), script.ingredientBoxes(emptyList()))
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
    fun `an island player down to 1 hitpoint is spared`() {
        assertTrue(script.sparesPlayer(CUT_TREE, hitpoints = 1))
    }

    @Test
    fun `an island player with hitpoints to spare is hit as rolled`() {
        assertFalse(script.sparesPlayer(CUT_TREE, hitpoints = 2))
    }

    @Test
    fun `a player who finished the island is not spared`() {
        assertFalse(script.sparesPlayer(DONE, hitpoints = 1))
    }

    @Test
    fun `a door stays locked before its step`() {
        assertEquals(PassageOutcome.Locked(TutorialFixtures.LOCKED), script.openDoor(door, TALK_TO_GUIDE))
    }

    @Test
    fun `going through a door at its step moves the player on`() {
        assertEquals(PassageOutcome.Pass(FIND_SURVIVAL_EXPERT), script.openDoor(door, OPEN_HOUSE_DOOR))
    }

    @Test
    fun `a ladder is refused before its step`() {
        assertEquals(PassageOutcome.Locked(TutorialFixtures.NOT_READY), script.climbLadder(TutorialFixtures.ladder, TutorialStep.TALK_ABOUT_QUESTS))
    }

    @Test
    fun `climbing a ladder at its step moves the player on`() {
        assertEquals(
            PassageOutcome.Pass(TutorialStep.TALK_TO_MINING_INSTRUCTOR),
            script.climbLadder(TutorialFixtures.ladder, TutorialStep.ENTER_MINE),
        )
    }

    @Test
    fun `going through a door again later moves nobody on`() {
        assertEquals(PassageOutcome.Pass(null), script.openDoor(door, FIND_SURVIVAL_EXPERT))
    }

    @Test
    fun `data without one of the instructors' dialogues is refused`() {
        val withoutExpert = data.copy(dialogues = data.dialogues - TutorialScript.SURVIVAL_DONE)

        assertThrows<IllegalArgumentException> { TutorialScript(withoutExpert) }
    }
}
