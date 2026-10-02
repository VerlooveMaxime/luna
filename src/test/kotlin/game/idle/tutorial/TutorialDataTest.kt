package game.idle.tutorial

import game.idle.tutorial.TutorialFixtures.DOOR
import game.idle.tutorial.TutorialFixtures.HELP
import game.idle.tutorial.TutorialFixtures.json
import game.idle.tutorial.TutorialFixtures.steps
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TutorialDataTest {

    private fun refused(jsonc: String) = assertThrows<IllegalArgumentException> { TutorialData.parse(jsonc) }

    private fun step(screen: String) = json(steps = steps(TALK_TO_GUIDE to screen))

    private fun dialogue(boxes: String) = json(dialogues = TutorialFixtures.DIALOGUES + """, "talk": [ $boxes ]""")

    @Test
    fun `the start tile is read`() {
        assertEquals(Position(3200, 3200), TutorialData.parse(json()).start)
    }

    @Test
    fun `a step's help box, npc arrow and tabs are read`() {
        val data = TutorialData.parse(step("""{ "help": $HELP, "arrow": { "npc": 945 }, "tabs": ["LOGOUT", "SETTINGS"] }"""))

        val expected = StepScreen(HelpBox("Title", listOf("Line.")), HintTarget.Npc(945), listOf(TabIndex.LOGOUT, TabIndex.SETTINGS))
        assertEquals(expected, data.steps.getValue(TALK_TO_GUIDE))
    }

    @Test
    fun `a tile arrow is read with its edge and height`() {
        val data = TutorialData.parse(step("""{ "help": $HELP, "arrow": { "tile": { "x": 3098, "y": 3107 }, "edge": "WEST", "height": 128 } }"""))

        assertEquals(HintTarget.Tile(Position(3098, 3107), TileEdge.WEST, 128), data.steps.getValue(TALK_TO_GUIDE).arrow)
    }

    @Test
    fun `a tile arrow points at the centre of its tile unless told otherwise`() {
        val data = TutorialData.parse(step("""{ "help": $HELP, "arrow": { "tile": { "x": 3098, "y": 3107 } } }"""))

        assertEquals(HintTarget.Tile(Position(3098, 3107), TileEdge.CENTRE, 0), data.steps.getValue(TALK_TO_GUIDE).arrow)
    }

    @Test
    fun `a step without an arrow shows none`() {
        assertEquals(HintTarget.None, TutorialData.parse(json()).steps.getValue(TALK_TO_GUIDE).arrow)
    }

    @Test
    fun `an arrow at an npc and a tile at once is refused`() {
        refused(step("""{ "help": $HELP, "arrow": { "npc": 945, "tile": { "x": 3098, "y": 3107 } } }"""))
    }

    @Test
    fun `an arrow at nothing is refused`() {
        refused(step("""{ "help": $HELP, "arrow": {} }"""))
    }

    @Test
    fun `a file without a start tile is refused`() {
        refused("""{ "steps": { ${steps()} } }""")
    }

    @Test
    fun `an empty file is refused`() {
        refused("")
    }

    @Test
    fun `a step without a help box is refused`() {
        refused(step("{}"))
    }

    @Test
    fun `a help box without a title is refused`() {
        refused(step("""{ "help": { "lines": ["Line."] } }"""))
    }

    @Test
    fun `a help box without lines is refused`() {
        refused(step("""{ "help": { "title": "Title" } }"""))
    }

    @Test
    fun `a help box with five lines is refused`() {
        refused(step("""{ "help": { "title": "Title", "lines": ["1", "2", "3", "4", "5"] } }"""))
    }

    @Test
    fun `a step the tutorial does not have is refused`() {
        refused(json(steps = steps() + """, "SWIM_ACROSS": { "help": $HELP }"""))
    }

    @Test
    fun `a file missing a step is refused`() {
        refused(json(steps = """"DESIGN_CHARACTER": { "help": $HELP }"""))
    }

    @Test
    fun `npc, player and text boxes are read in order`() {
        val data = TutorialData.parse(dialogue("""{ "npc": ["Hi."] }, { "player": ["Hello."] }, { "text": ["Done."] }"""))

        val expected = listOf(DialogueBox.Npc(listOf("Hi.")), DialogueBox.Player(listOf("Hello.")), DialogueBox.Text(listOf("Done.")))
        assertEquals(expected, data.dialogues.getValue("talk"))
    }

    @Test
    fun `a box said by two speakers is refused`() {
        refused(dialogue("""{ "npc": ["Hi."], "player": ["Hello."] }"""))
    }

    @Test
    fun `a box said by nobody is refused`() {
        refused(dialogue("{}"))
    }

    @Test
    fun `a box without lines is refused`() {
        refused(dialogue("""{ "npc": [] }"""))
    }

    @Test
    fun `a box with five lines is refused`() {
        refused(dialogue("""{ "npc": ["1", "2", "3", "4", "5"] }"""))
    }

    @Test
    fun `a door is read with its wall, open door and steps`() {
        val door = TutorialData.parse(json(doors = DOOR)).doors.single()

        assertEquals(TutorialFixtures.door.copy(locked = "locked"), door)
    }

    @Test
    fun `a door without an id is refused`() {
        refused(json(doors = DOOR.replace(""""id": 3014, """, "")))
    }

    @Test
    fun `a door without an open door is refused`() {
        refused(json(doors = DOOR.replace(""""open_id": 1535, """, "")))
    }

    @Test
    fun `a door without a tile is refused`() {
        refused(json(doors = DOOR.replace(""""tile": { "x": 3205, "y": 3200 }, """, "")))
    }

    @Test
    fun `a door naming a dialogue that does not exist is refused`() {
        refused(json(doors = DOOR.replace(""""locked": "locked"""", """"locked": "nowhere"""")))
    }

    @Test
    fun `a door opening at a step that does not exist is refused`() {
        refused(json(doors = DOOR.replace("OPEN_HOUSE_DOOR", "SWIM_ACROSS")))
    }

    @Test
    fun `a door's steps are read as tutorial steps`() {
        assertEquals(OPEN_HOUSE_DOOR, TutorialData.parse(json(doors = DOOR)).doors.single().opensAt)
    }
}
