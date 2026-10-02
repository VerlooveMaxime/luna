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
    fun `a help box may have no title`() {
        val data = TutorialData.parse(step("""{ "help": { "lines": ["You gained some experience."] } }"""))

        assertEquals("", data.steps.getValue(TALK_TO_GUIDE).help.title)
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
    fun `a step's flashing tab is read`() {
        val data = TutorialData.parse(step("""{ "help": $HELP, "tabs": ["INVENTORY"], "flash": "INVENTORY" }"""))

        assertEquals(TabIndex.INVENTORY, data.steps.getValue(TALK_TO_GUIDE).flash)
    }

    @Test
    fun `a step flashes no tab unless told to`() {
        assertEquals(null, TutorialData.parse(json()).steps.getValue(TALK_TO_GUIDE).flash)
    }

    @Test
    fun `an item box is read with its items and lines`() {
        val data = TutorialData.parse(dialogue("""{ "items": [590, 1351], "text": ["Both."] }"""))

        assertEquals(listOf(DialogueBox.Items(listOf(590, 1351), listOf("Both."))), data.dialogues.getValue("talk"))
    }

    @Test
    fun `items said by an npc are refused`() {
        refused(dialogue("""{ "items": [590], "npc": ["Hi."] }"""))
    }

    @Test
    fun `an item box without items is refused`() {
        refused(dialogue("""{ "items": [], "text": ["Nothing."] }"""))
    }

    @Test
    fun `an item box with three items is refused`() {
        refused(dialogue("""{ "items": [590, 1351, 303], "text": ["All."] }"""))
    }

    @Test
    fun `help boxes to wait with are read by activity`() {
        val data = TutorialData.parse(json(busy = """"fishing": { "title": "Please wait...", "lines": ["Fishing."] }"""))

        assertEquals(mapOf("fishing" to HelpBox("Please wait...", listOf("Fishing."))), data.busy)
    }

    @Test
    fun `quiet chat lines are read`() {
        val data = TutorialData.parse(json(quiet = """"You get some logs.""""))

        assertEquals(setOf("You get some logs."), data.quietMessages)
    }

    @Test
    fun `chat messages are read by name`() {
        val data = TutorialData.parse(json(messages = """"burnt": "Burnt.""""))

        assertEquals(mapOf("burnt" to "Burnt."), data.messages)
    }

    @Test
    fun `a door is read with its closed leaves, open pieces and steps`() {
        val door = TutorialData.parse(json(doors = DOOR)).doors.single()

        assertEquals(TutorialFixtures.door.copy(locked = "locked"), door)
    }

    @Test
    fun `a door without closed leaves is refused`() {
        refused(json(doors = DOOR.replace(Regex(""""closed": \[[^\]]*\],"""), "")))
    }

    @Test
    fun `a door without open pieces is refused`() {
        refused(json(doors = DOOR.replace(Regex(""""open": \[[^\]]*\],"""), "")))
    }

    @Test
    fun `a closed leaf without an id is refused`() {
        refused(json(doors = DOOR.replace(""""id": 3014, """, "")))
    }

    @Test
    fun `a closed leaf without a tile is refused`() {
        refused(json(doors = DOOR.replace(""""tile": { "x": 3205, "y": 3200 }, """, "")))
    }

    @Test
    fun `an open piece without an id is refused`() {
        refused(json(doors = DOOR.replace(""""id": 1535, """, "")))
    }

    @Test
    fun `an open piece without a rotation is refused`() {
        refused(json(doors = DOOR.replace(""", "rotation": 1""", "")))
    }

    @Test
    fun `an open piece turned past a quarter turn short of a full turn is refused`() {
        refused(json(doors = DOOR.replace(""""rotation": 1""", """"rotation": 4""")))
    }

    @Test
    fun `an open piece without a tile is refused`() {
        refused(json(doors = DOOR.replace(""""tile": { "x": 3204, "y": 3200 }, """, "")))
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
