package game.idle.tutorial

import game.harness.RecordedMessage
import game.idle.tutorial.TutorialScript.Companion.BRONZE_AXE
import game.idle.tutorial.TutorialScript.Companion.BURNT_FISH
import game.idle.tutorial.TutorialScript.Companion.RAW_SHRIMPS
import game.idle.tutorial.TutorialScript.Companion.SHRIMPS
import game.idle.tutorial.TutorialScript.Companion.SMALL_FISHING_NET
import game.idle.tutorial.TutorialScript.Companion.TINDERBOX
import game.idle.idleState
import game.idle.tutorial.TutorialStep.BUILD_FIRST_FLOW
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
import game.idle.tutorial.TutorialStep.OPEN_IDLE_TAB
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.TALK_ABOUT_FOOD
import game.idle.tutorial.TutorialStep.TALK_ABOUT_LOOP
import game.idle.tutorial.TutorialStep.WATCH_THE_AUTOPILOT
import game.idle.ui.FlowWidgets
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.player.login.firstLogin
import game.skill.firemaking.LightAction
import game.testworld.TestWorld
import io.luna.Luna
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.PlayerAppearance
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LunaTutorialTest {

    private val data = TutorialFixtures.data
    private val door = TutorialFixtures.door
    private val leaf = door.closed.single()
    private val gate = TutorialFixtures.gate
    private val elsewhere = Position(3240, 3240)
    private val besideElsewhere = Position(3241, 3240)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun tutorial() = LunaTutorial(TutorialScript(data), data, TestWorld.world)

    private fun newCharacter(): Player = TestWorld.login("tutee", elsewhere)

    private fun returning(step: TutorialStep, position: Position = elsewhere): Player =
        TestWorld.login("tutee", position).also {
            it.firstLogin = false
            it.tutorialStep = step
        }

    /** Logs [player] in to the tutorial and lets the tick after login pass. */
    private fun loggedIn(player: Player, tutorial: LunaTutorial = tutorial()): Player {
        tutorial.onLogin(player)
        TestWorld.tick()
        return player
    }

    private fun sent(player: Player, type: String): List<RecordedMessage> = TestWorld.messages(player).filter { it.type == type }

    private fun texts(player: Player): List<String> = sent(player, "WidgetTextMessageWriter").map { it.fields.getValue("text").toString() }

    private fun arrow(player: Player): Map<String, Any> = sent(player, "HintArrowMessageWriter").last().fields

    /** Luna also sends these for chunks coming into view, so tests count what one call or tick adds. */
    private fun chunkUpdates(player: Player): Int = sent(player, "GroupedEntityMessageWriter").size

    private fun carried(player: Player, id: Int): Int = player.inventory.computeAmountForId(id)

    /** What clicking "Click here to continue" does, as `continueDialogue.kts` handles it. */
    private fun continueDialogue(player: Player) {
        player.overlays.getOverlay(DialogueInterface::class.java).isContinueClicked = true
        player.dialogues?.advance() ?: player.overlays.closeWindows()
    }

    private fun readToTheEnd(player: Player) {
        while (player.overlays.has(DialogueInterface::class.java)) {
            continueDialogue(player)
        }
    }

    private fun talkToExpert(tutorial: LunaTutorial, player: Player) {
        tutorial.talkToSurvivalExpert(player, TestWorld.spawnNpc(TutorialFixtures.EXPERT, besideElsewhere))
        readToTheEnd(player)
    }

    private fun gainExperience(tutorial: LunaTutorial, player: Player, skill: Int, amount: Double) {
        val before = player.skills.getSkill(skill).experience
        player.skills.getSkill(skill).experience = before + amount
        tutorial.experienceChanged(player, SkillChangeEvent(player, before, 1, 1, skill))
    }

    /** Uses an item on an object placed beside the player; true when the click goes on to Luna's own handlers. */
    private fun useOn(tutorial: LunaTutorial, player: Player, itemId: Int, objectId: Int = FIRE): Boolean {
        val target = TestWorld.place(objectId, besideElsewhere)
        return tutorial.allows(player, ItemOnObjectEvent(player, itemId, 0, INVENTORY, target))
    }

    private fun cookingPlayer(step: TutorialStep): Player =
        returning(step).also { it.inventory.add(Item(RAW_SHRIMPS)) }

    /** A tinderbox struck forever: the fire never catches. */
    private class EndlessLighting(player: Player) : LightAction(player) {
        override fun catches(): Boolean = false

        override fun onLight() = Unit
    }

    private fun startLighting(player: Player) {
        player.inventory.add(Item(TINDERBOX))
        player.submitAction(EndlessLighting(player))
    }

    private fun helpTitles(player: Player): List<String> =
        sent(player, "WidgetTextMessageWriter").filter { it.fields["id"] == LunaTutorial.HELP_TITLE }.map { it.fields.getValue("text").toString() }

    private companion object {
        const val FIRE = 2732
        const val RANGE = 114
        const val LOGS = 1511
        const val INVENTORY = 3214
        const val INVENTORY_SIZE = 28
    }

    @Test
    fun `a new character starts at the character designer`() {
        val player = newCharacter()

        tutorial().onLogin(player)

        assertEquals(DESIGN_CHARACTER, player.tutorialStep)
    }

    @Test
    fun `a new character is not new at its next login`() {
        val player = newCharacter()

        tutorial().onLogin(player)

        assertFalse(player.firstLogin)
    }

    @Test
    fun `a new character appears at the start tile`() {
        val player = newCharacter()

        tutorial().onLogin(player)

        assertEquals(data.start, player.position)
    }

    @Test
    fun `a new character sees the character designer`() {
        val player = newCharacter()

        tutorial().onLogin(player)

        assertTrue(player.overlays.has(DesignPlayerInterface::class.java))
    }

    @Test
    fun `a character that left in the designer starts again at the start tile`() {
        val player = returning(DESIGN_CHARACTER)

        tutorial().onLogin(player)

        assertEquals(data.start, player.position)
    }

    @Test
    fun `a character mid-tutorial keeps its place`() {
        val player = returning(OPEN_HOUSE_DOOR)

        tutorial().onLogin(player)

        assertEquals(elsewhere, player.position)
    }

    @Test
    fun `a character mid-tutorial is kept on the tutorial`() {
        val player = returning(OPEN_HOUSE_DOOR)

        tutorial().onLogin(player)

        assertTrue(player.controllers.primary is TutorialController)
    }

    @Test
    fun `a character that finished the tutorial is left alone`() {
        val player = returning(DONE)

        tutorial().onLogin(player)

        assertFalse(player.controllers.primary is TutorialController)
    }

    @Test
    fun `after login only the side tabs of the steps so far are shown`() {
        val player = loggedIn(returning(CUT_TREE))

        val shown = TabIndex.values().filter { player.tabs.get(it).isPresent }

        assertEquals(listOf(TabIndex.INVENTORY, TabIndex.LOGOUT, TabIndex.SETTINGS), shown)
    }

    @Test
    fun `side tabs wait a tick for Luna's own login to send its tabs`() {
        val player = returning(CUT_TREE)

        tutorial().onLogin(player)

        assertEquals(emptyList<RecordedMessage>(), sent(player, "TabInterfaceMessageWriter"))
    }

    @Test
    fun `the step's tab flashes again after login`() {
        val player = loggedIn(returning(OPEN_INVENTORY))

        assertEquals("INVENTORY", sent(player, "FlashTabMessageWriter").single().fields["tab"])
    }

    @Test
    fun `the Idle tab stays hidden before its step`() {
        val player = loggedIn(returning(OPEN_SKILLS))

        assertFalse(player.tabs.get(TabIndex.UNUSED).isPresent)
    }

    @Test
    fun `the Idle tab appears with its own widgets at its step, and flashes`() {
        val player = loggedIn(returning(OPEN_IDLE_TAB))

        assertEquals(FlowWidgets.TAB, player.tabs.get(TabIndex.UNUSED).asInt)
        assertEquals("UNUSED", sent(player, "FlashTabMessageWriter").single().fields["tab"])
    }

    @Test
    fun `a lesson moves on once the player's autopilot reaches its goal`() {
        val player = loggedIn(returning(BUILD_FIRST_FLOW))

        player.idleState = player.idleState.copy(flow = listOf("chop 1 normal", " Light 1"), running = true)
        TestWorld.tick()

        assertEquals(WATCH_THE_AUTOPILOT, player.tutorialStep)
    }

    @Test
    fun `a lesson waits while the autopilot is short of its goal`() {
        val player = loggedIn(returning(BUILD_FIRST_FLOW))

        player.idleState = player.idleState.copy(flow = listOf("chop normal", "light 1"), running = true)
        TestWorld.tick()

        assertEquals(BUILD_FIRST_FLOW, player.tutorialStep)
    }

    @Test
    fun `a developer jump moves a player on the island to the step named, whatever the case`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        val reply = tutorial.jumpTo(player, "build_first_flow")

        assertEquals(listOf("Tutorial step: BUILD_FIRST_FLOW", "BUILD_FIRST_FLOW"), listOf(reply, player.tutorialStep.name))
    }

    @Test
    fun `a jump to a step that does not exist says so`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        assertEquals("No tutorial step 'moon'.", tutorial.jumpTo(player, "moon"))
        assertEquals(CUT_TREE, player.tutorialStep)
    }

    @Test
    fun `a jump neither leaves the island nor brings a player back to it`() {
        val tutorial = tutorial()
        val onIsland = loggedIn(returning(CUT_TREE), tutorial)
        val done = TestWorld.login("done", besideElsewhere).also { it.firstLogin = false }

        val leaving = tutorial.jumpTo(onIsland, "done")
        val coming = tutorial.jumpTo(done, "cut_tree")

        assertEquals("The jump only moves a player still on the island to another step on it.", leaving)
        assertEquals(leaving, coming)
        assertEquals(listOf(CUT_TREE, DONE), listOf(onIsland.tutorialStep, done.tutorialStep))
    }

    @Test
    fun `no tab flashes at a step that has none to click`() {
        val player = loggedIn(returning(CUT_TREE))

        assertEquals(emptyList<RecordedMessage>(), sent(player, "FlashTabMessageWriter"))
    }

    @Test
    fun `the help box shows the step's title and lines, blank below them`() {
        val player = loggedIn(returning(OPEN_HOUSE_DOOR))

        assertEquals(listOf("Doors", "Open the door.", "", "", ""), texts(player))
    }

    @Test
    fun `the help box texts go to the help box widgets`() {
        val player = loggedIn(returning(OPEN_HOUSE_DOOR))

        val ids = sent(player, "WidgetTextMessageWriter").map { it.fields.getValue("id") }

        assertEquals(listOf(LunaTutorial.HELP_TITLE) + LunaTutorial.HELP_LINES, ids)
    }

    @Test
    fun `the help box opens in the chatbox after its texts`() {
        val player = loggedIn(returning(OPEN_HOUSE_DOOR))

        val types = TestWorld.messages(player).map { it.type }

        assertTrue(types.lastIndexOf("WidgetTextMessageWriter") < types.indexOf("StickyChatboxMessageWriter"))
    }

    @Test
    fun `the help box opens the tutorial's chatbox interface`() {
        val player = loggedIn(returning(OPEN_HOUSE_DOOR))

        assertEquals(LunaTutorial.HELP_BOX, sent(player, "StickyChatboxMessageWriter").single().fields["id"])
    }

    @Test
    fun `the arrow points at the step's npc`() {
        val guide = TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3201, 3200))

        val player = loggedIn(returning(TALK_TO_GUIDE))

        assertEquals(mapOf("type" to 1, "index" to guide.index), arrow(player).filterKeys { it in setOf("type", "index") })
    }

    @Test
    fun `the arrow points at the nearest of several npcs the step names`() {
        TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3230, 3240))
        val nearest = TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3238, 3240))
        TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3260, 3240))

        val player = loggedIn(returning(TALK_TO_GUIDE))

        assertEquals(nearest.index, arrow(player)["index"])
    }

    @Test
    fun `the arrow skips nearer npcs of another kind`() {
        val guide = TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3250, 3240))
        TestWorld.spawnNpc(TutorialFixtures.EXPERT, besideElsewhere)

        val player = loggedIn(returning(TALK_TO_GUIDE))

        assertEquals(guide.index, arrow(player)["index"])
    }

    @Test
    fun `no arrow shows when the step's npc is not in the world`() {
        val player = loggedIn(returning(TALK_TO_GUIDE))

        assertEquals(0, arrow(player)["type"])
    }

    @Test
    fun `a tile arrow points at the step's tile, edge and height`() {
        val player = loggedIn(returning(OPEN_HOUSE_DOOR))

        assertEquals(mapOf("type" to 3, "index" to 0, "x" to 3205, "y" to 3200, "height" to 128), arrow(player))
    }

    @Test
    fun `a step without an arrow hides it`() {
        val player = loggedIn(returning(LIGHT_FIRE))

        assertEquals(0, arrow(player)["type"])
    }

    @Test
    fun `closing the designer moves a new character on to the guide`() {
        val player = loggedIn(newCharacter())

        player.overlays.closeWindows()
        TestWorld.tick()

        assertEquals(TALK_TO_GUIDE, player.tutorialStep)
    }

    @Test
    fun `a character still in the designer stays at the design step`() {
        val player = loggedIn(newCharacter())

        TestWorld.tick()

        assertEquals(DESIGN_CHARACTER, player.tutorialStep)
    }

    @Test
    fun `moving on shows the next step's help box`() {
        val player = loggedIn(newCharacter())

        player.overlays.closeWindows()
        TestWorld.tick()

        assertEquals("Talk to the guide.", texts(player)[texts(player).lastIndexOf("Getting started") + 1])
    }

    @Test
    fun `talking to the guide opens his welcome`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE), tutorial)

        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, besideElsewhere))

        assertTrue("Welcome." in texts(player))
    }

    @Test
    fun `the welcome moves nobody on before it is read`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE), tutorial)

        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, besideElsewhere))

        assertEquals(TALK_TO_GUIDE, player.tutorialStep)
    }

    @Test
    fun `reading the welcome to the end sends the player to the door`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE), tutorial)
        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, besideElsewhere))

        readToTheEnd(player)

        assertEquals(OPEN_HOUSE_DOOR, player.tutorialStep)
    }

    @Test
    fun `the guide only repeats where the door is once he has welcomed the player`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR), tutorial)
        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, besideElsewhere))

        readToTheEnd(player)

        assertEquals(OPEN_HOUSE_DOOR, player.tutorialStep)
    }

    @Test
    fun `talking to the Survival Expert opens her dialogue`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT), tutorial)

        tutorial.talkToSurvivalExpert(player, TestWorld.spawnNpc(TutorialFixtures.EXPERT, besideElsewhere))

        assertTrue("I'm the Survival Expert." in texts(player))
    }

    @Test
    fun `reading the Survival Expert's welcome sends the player to their backpack`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT), tutorial)

        talkToExpert(tutorial, player)

        assertEquals(OPEN_INVENTORY, player.tutorialStep)
    }

    @Test
    fun `the Survival Expert hands over a net when she talks about food`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_ABOUT_FOOD), tutorial)

        talkToExpert(tutorial, player)

        assertEquals(1, carried(player, SMALL_FISHING_NET))
    }

    @Test
    fun `the Survival Expert hands back a lost axe`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)
        player.inventory.add(Item(TINDERBOX))

        talkToExpert(tutorial, player)

        assertEquals(1, carried(player, BRONZE_AXE))
    }

    @Test
    fun `the Survival Expert does not hand out a tool the player still carries`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)
        player.inventory.add(Item(BRONZE_AXE))
        player.inventory.add(Item(TINDERBOX))

        talkToExpert(tutorial, player)

        assertEquals(1, carried(player, TINDERBOX))
    }

    @Test
    fun `a worn axe is not handed out again`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)
        player.equipment.set(Equipment.WEAPON, Item(BRONZE_AXE))

        talkToExpert(tutorial, player)

        assertEquals(0, carried(player, BRONZE_AXE))
    }

    @Test
    fun `the Survival Expert hands back a lost tool as soon as she is talked to`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)

        tutorial.talkToSurvivalExpert(player, TestWorld.spawnNpc(TutorialFixtures.EXPERT, besideElsewhere))

        assertEquals(1, carried(player, BRONZE_AXE))
    }

    @Test
    fun `a tool handed back is shown in a box after her lines`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)
        player.inventory.add(Item(TINDERBOX))

        talkToExpert(tutorial, player)

        assertTrue("An axe." in texts(player))
    }

    @Test
    fun `a tool that does not fit is dropped at the player's feet`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LIGHT_FIRE), tutorial)
        repeat(INVENTORY_SIZE) { player.inventory.add(Item(LOGS)) }

        talkToExpert(tutorial, player)

        val dropped = TestWorld.world.items.filter { it.position == player.position }.map { it.id }.toSet()
        assertEquals(setOf(BRONZE_AXE, TINDERBOX), dropped)
    }

    @Test
    fun `opening the flashing backpack hands over the axe and tinderbox`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_INVENTORY), tutorial)

        tutorial.tabOpened(player, TabIndex.INVENTORY)

        assertEquals(listOf(1, 1), listOf(carried(player, BRONZE_AXE), carried(player, TINDERBOX)))
    }

    @Test
    fun `opening the flashing backpack sends the player to the tree`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_INVENTORY), tutorial)

        tutorial.tabOpened(player, TabIndex.INVENTORY)

        assertEquals(CUT_TREE, player.tutorialStep)
    }

    @Test
    fun `opening another tab moves nobody on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_INVENTORY), tutorial)

        tutorial.tabOpened(player, TabIndex.SKILL)

        assertEquals(OPEN_INVENTORY, player.tutorialStep)
    }

    @Test
    fun `the first log sends the player on to lighting a fire`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        gainExperience(tutorial, player, Skill.WOODCUTTING, 25.0)

        assertEquals(LIGHT_FIRE, player.tutorialStep)
    }

    @Test
    fun `a skill change that gives no experience moves nobody on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        gainExperience(tutorial, player, Skill.WOODCUTTING, 0.0)

        assertEquals(CUT_TREE, player.tutorialStep)
    }

    @Test
    fun `the help box says to wait while the player lights a fire`() {
        val player = loggedIn(returning(LIGHT_FIRE))

        startLighting(player)
        TestWorld.tick()

        assertEquals("Please wait...", helpTitles(player).last())
    }

    @Test
    fun `the help box waits for him when the character is a man`() {
        val player = loggedIn(returning(LIGHT_FIRE))

        startLighting(player)
        TestWorld.tick()

        assertTrue("Lighting, he says." in texts(player))
    }

    @Test
    fun `the help box waits for her when the character is a woman`() {
        val player = loggedIn(returning(LIGHT_FIRE))
        player.appearance.set(PlayerAppearance.GENDER, PlayerAppearance.GENDER_FEMALE)

        startLighting(player)
        TestWorld.tick()

        assertTrue("Lighting, she says." in texts(player))
    }

    @Test
    fun `no wait box shows while the autopilot does the work`() {
        val player = loggedIn(returning(LIGHT_FIRE))
        player.idleState = player.idleState.copy(running = true)

        startLighting(player)
        TestWorld.tick()

        assertFalse("Please wait..." in helpTitles(player))
    }

    @Test
    fun `the wait box is sent once while the player keeps at it`() {
        val player = loggedIn(returning(LIGHT_FIRE))
        startLighting(player)
        TestWorld.tick()

        TestWorld.tick(2)

        assertEquals(1, helpTitles(player).count { it == "Please wait..." })
    }

    @Test
    fun `the step's help box comes back once the player stops`() {
        val player = loggedIn(returning(LIGHT_FIRE))
        startLighting(player)
        TestWorld.tick()

        player.actions.getAll(LightAction::class.java).forEach { it.interrupt() }
        TestWorld.tick()

        assertEquals(data.steps.getValue(LIGHT_FIRE).help.title, helpTitles(player).last())
    }

    @Test
    fun `a chat line the island never sent stays out of the chatbox`() {
        val player = loggedIn(returning(CUT_TREE))

        player.sendMessage("You get some logs.")

        assertFalse("You get some logs." in TestWorld.chatbox(player))
    }

    @Test
    fun `other chat lines still reach the chatbox on the island`() {
        val player = loggedIn(returning(CUT_TREE))

        player.sendMessage("You need an axe to chop this tree.")

        assertTrue("You need an axe to chop this tree." in TestWorld.chatbox(player))
    }

    @Test
    fun `a player who finished the tutorial gets every chat line`() {
        val player = loggedIn(returning(DONE))

        player.sendMessage("You get some logs.")

        assertTrue("You get some logs." in TestWorld.chatbox(player))
    }

    @Test
    fun `the first raw shrimp on a fire is cooked by the tutorial, not by Luna`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        assertFalse(useOn(tutorial, player, RAW_SHRIMPS))
    }

    @Test
    fun `the first shrimp always burns`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertEquals(listOf(0, 1), listOf(carried(player, RAW_SHRIMPS), carried(player, BURNT_FISH)))
    }

    @Test
    fun `the burnt shrimp is announced`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertTrue("Burnt." in TestWorld.chatbox(player))
    }

    @Test
    fun `burning the first shrimp sends the player to cook another`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertEquals(COOK_AGAIN, player.tutorialStep)
    }

    @Test
    fun `the second shrimp always cooks`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_AGAIN), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertEquals(1, carried(player, SHRIMPS))
    }

    @Test
    fun `the cooked shrimp gives cooking experience`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_AGAIN), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        val expected = TutorialScript.SHRIMP_EXPERIENCE * Luna.settings().game().experienceMultiplier()
        assertEquals(expected, player.skills.getSkill(Skill.COOKING).experience)
    }

    @Test
    fun `the cooked shrimp is announced`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_AGAIN), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertTrue("Cooked." in TestWorld.chatbox(player))
    }

    @Test
    fun `cooking the second shrimp sends the player to Brynna's last lesson`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_AGAIN), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        TestWorld.tick()

        assertEquals(TALK_ABOUT_LOOP, player.tutorialStep)
    }

    @Test
    fun `a shrimp dropped on the way to the fire is not cooked`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        useOn(tutorial, player, RAW_SHRIMPS)
        player.inventory.remove(Item(RAW_SHRIMPS))
        TestWorld.tick()

        assertEquals(COOK_SHRIMP, player.tutorialStep)
    }

    @Test
    fun `raw shrimp on a fire after the lesson are left to Luna's cooking`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(LEAVE_SURVIVAL_AREA), tutorial)

        assertTrue(useOn(tutorial, player, RAW_SHRIMPS))
    }

    @Test
    fun `another item on a fire is left to Luna`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        assertTrue(useOn(tutorial, player, LOGS))
    }

    @Test
    fun `raw shrimp on something that is not a fire are left to Luna`() {
        val tutorial = tutorial()
        val player = loggedIn(cookingPlayer(COOK_SHRIMP), tutorial)

        assertTrue(useOn(tutorial, player, RAW_SHRIMPS, objectId = RANGE))
    }

    @Test
    fun `wielding is refused before the worn equipment tab exists`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        assertFalse(tutorial.allows(player, EquipItemEvent(player, 0, BRONZE_AXE, INVENTORY)))
    }

    @Test
    fun `a refused wield says why`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        tutorial.allows(player, EquipItemEvent(player, 0, BRONZE_AXE, INVENTORY))

        assertTrue("Not yet." in TestWorld.chatbox(player))
    }

    @Test
    fun `wielding is allowed once the tutorial is done`() {
        val tutorial = tutorial()
        val player = returning(DONE)

        assertTrue(tutorial.allows(player, EquipItemEvent(player, 0, BRONZE_AXE, INVENTORY)))
    }

    @Test
    fun `the tutorial's controller asks the tutorial about every click`() {
        val player = loggedIn(returning(CUT_TREE))

        assertFalse(player.controllers.checkEvent(EquipItemEvent(player, 0, BRONZE_AXE, INVENTORY)))
    }

    @Test
    fun `other clicks go on to Luna`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(CUT_TREE), tutorial)

        assertTrue(tutorial.allows(player, DropItemEvent(player, BRONZE_AXE, INVENTORY, 0)))
    }

    @Test
    fun `a locked door says why`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, leaf.tile), tutorial)

        tutorial.openDoor(player, door, leaf)

        assertTrue("Locked." in texts(player))
    }

    @Test
    fun `a locked door's message closes without moving the player on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, leaf.tile), tutorial)
        tutorial.openDoor(player, door, leaf)

        readToTheEnd(player)

        assertEquals(TALK_TO_GUIDE, player.tutorialStep)
    }

    @Test
    fun `a locked door keeps the player on their side`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, leaf.tile), tutorial)

        tutorial.openDoor(player, door, leaf)
        TestWorld.tick()

        assertEquals(leaf.tile, player.position)
    }

    @Test
    fun `an open door walks the player from its tile across the wall`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, leaf.tile), tutorial)

        tutorial.openDoor(player, door, leaf)
        TestWorld.tick()

        assertEquals(leaf.across, player.position)
    }

    @Test
    fun `an open door walks the player from across the wall onto its tile`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, leaf.across), tutorial)

        tutorial.openDoor(player, door, leaf)
        TestWorld.tick()

        assertEquals(leaf.tile, player.position)
    }

    @Test
    fun `the first time through the door moves the player on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, leaf.tile), tutorial)

        tutorial.openDoor(player, door, leaf)

        assertEquals(FIND_SURVIVAL_EXPERT, player.tutorialStep)
    }

    @Test
    fun `going through the door later moves nobody on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT, leaf.across), tutorial)

        tutorial.openDoor(player, door, leaf)

        assertEquals(FIND_SURVIVAL_EXPERT, player.tutorialStep)
    }

    @Test
    fun `a gate lets the player through at the leaf they clicked`() {
        val tutorial = tutorial()
        val lower = gate.closed[1]
        val player = loggedIn(returning(LEAVE_SURVIVAL_AREA, lower.tile), tutorial)

        tutorial.openDoor(player, gate, lower)
        TestWorld.tick()

        assertEquals(lower.across, player.position)
    }

    @Test
    fun `the first time through the gate moves the player on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LEAVE_SURVIVAL_AREA, gate.closed[0].tile), tutorial)

        tutorial.openDoor(player, gate, gate.closed[0])

        assertEquals(FIND_MASTER_CHEF, player.tutorialStep)
    }

    @Test
    fun `the player sees the door open at once`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, leaf.tile), tutorial)
        val before = chunkUpdates(player)

        tutorial.openDoor(player, door, leaf)

        assertEquals(before + 2, chunkUpdates(player))
    }

    @Test
    fun `the player sees every piece of a gate change`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(LEAVE_SURVIVAL_AREA, gate.closed[0].tile), tutorial)
        val before = chunkUpdates(player)

        tutorial.openDoor(player, gate, gate.closed[0])

        assertEquals(before + 4, chunkUpdates(player))
    }

    @Test
    fun `the player sees the door shut again on the third tick`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, leaf.tile), tutorial)
        tutorial.openDoor(player, door, leaf)
        TestWorld.tick(LunaTutorial.DOOR_OPEN_TICKS - 1)
        val before = chunkUpdates(player)

        TestWorld.tick()

        assertEquals(before + 2, chunkUpdates(player))
    }

    @Test
    fun `data without the tutorial's chat messages is refused`() {
        val withoutMessages = data.copy(messages = emptyMap())

        assertThrows<IllegalArgumentException> { LunaTutorial(TutorialScript(withoutMessages), withoutMessages, TestWorld.world) }
    }
}
