package game.idle.tutorial

import game.harness.RecordedMessage
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.DONE
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.player.login.firstLogin
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaTutorialTest {

    private val data = TutorialFixtures.data
    private val door = TutorialFixtures.door
    private val elsewhere = Position(3240, 3240)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private companion object {
        const val SURVIVAL_EXPERT = 943
    }

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

    /** Luna also sends these for chunks coming into view, so tests count what one call or tick adds. */
    private fun chunkUpdates(player: Player): Int = sent(player, "GroupedEntityMessageWriter").size

    private fun arrow(player: Player): Map<String, Any> = sent(player, "HintArrowMessageWriter").last().fields

    /** What clicking "Click here to continue" does, as `continueDialogue.kts` handles it. */
    private fun continueDialogue(player: Player) {
        player.overlays.getOverlay(DialogueInterface::class.java).isContinueClicked = true
        player.dialogues?.advance() ?: player.overlays.closeWindows()
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
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT))

        val shown = TabIndex.values().filter { player.tabs.get(it).isPresent }

        assertEquals(listOf(TabIndex.INVENTORY, TabIndex.LOGOUT, TabIndex.SETTINGS), shown)
    }

    @Test
    fun `side tabs wait a tick for Luna's own login to send its tabs`() {
        val player = returning(FIND_SURVIVAL_EXPERT)

        tutorial().onLogin(player)

        assertEquals(emptyList<RecordedMessage>(), sent(player, "TabInterfaceMessageWriter"))
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
        TestWorld.spawnNpc(SURVIVAL_EXPERT, Position(3241, 3240))

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
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT))

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

        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3241, 3240)))

        assertTrue("Welcome." in texts(player))
    }

    @Test
    fun `the welcome moves nobody on before it is read`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE), tutorial)

        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3241, 3240)))

        assertEquals(TALK_TO_GUIDE, player.tutorialStep)
    }

    @Test
    fun `reading the welcome to the end sends the player to the door`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE), tutorial)
        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3241, 3240)))

        continueDialogue(player)
        continueDialogue(player)

        assertEquals(OPEN_HOUSE_DOOR, player.tutorialStep)
    }

    @Test
    fun `the guide only repeats where the door is once he has welcomed the player`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR), tutorial)
        tutorial.talkToGuide(player, TestWorld.spawnNpc(TutorialFixtures.GUIDE, Position(3241, 3240)))

        continueDialogue(player)

        assertEquals(OPEN_HOUSE_DOOR, player.tutorialStep)
    }

    @Test
    fun `a locked door says why`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, door.tile), tutorial)

        tutorial.openDoor(player, door)

        assertTrue("Locked." in texts(player))
    }

    @Test
    fun `a locked door's message closes without moving the player on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, door.tile), tutorial)
        tutorial.openDoor(player, door)

        continueDialogue(player)

        assertEquals(TALK_TO_GUIDE, player.tutorialStep)
    }

    @Test
    fun `a locked door keeps the player on their side`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(TALK_TO_GUIDE, door.tile), tutorial)

        tutorial.openDoor(player, door)
        TestWorld.tick()

        assertEquals(door.tile, player.position)
    }

    @Test
    fun `an open door walks the player from its tile across the wall`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, door.tile), tutorial)

        tutorial.openDoor(player, door)
        TestWorld.tick()

        assertEquals(door.across, player.position)
    }

    @Test
    fun `an open door walks the player from across the wall onto its tile`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, door.across), tutorial)

        tutorial.openDoor(player, door)
        TestWorld.tick()

        assertEquals(door.tile, player.position)
    }

    @Test
    fun `the first time through the door moves the player on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, door.tile), tutorial)

        tutorial.openDoor(player, door)

        assertEquals(FIND_SURVIVAL_EXPERT, player.tutorialStep)
    }

    @Test
    fun `going through the door later moves nobody on`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(FIND_SURVIVAL_EXPERT, door.across), tutorial)

        tutorial.openDoor(player, door)

        assertEquals(FIND_SURVIVAL_EXPERT, player.tutorialStep)
    }

    @Test
    fun `the player sees the door open at once`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, door.tile), tutorial)
        val before = chunkUpdates(player)

        tutorial.openDoor(player, door)

        assertEquals(before + 2, chunkUpdates(player))
    }

    @Test
    fun `the player sees the door shut again on the third tick`() {
        val tutorial = tutorial()
        val player = loggedIn(returning(OPEN_HOUSE_DOOR, door.tile), tutorial)
        tutorial.openDoor(player, door)
        TestWorld.tick(LunaTutorial.DOOR_OPEN_TICKS - 1)
        val before = chunkUpdates(player)

        TestWorld.tick()

        assertEquals(before + 2, chunkUpdates(player))
    }
}
