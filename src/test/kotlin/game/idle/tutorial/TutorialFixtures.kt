package game.idle.tutorial

import game.idle.tutorial.TutorialStep.CUT_TREE
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.FIND_MASTER_CHEF
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.LEAVE_SURVIVAL_AREA
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/** A small island inside `TestWorld`'s open map, shaped like `tutorial.jsonc`. */
object TutorialFixtures {

    const val GUIDE = TutorialScript.RUNESCAPE_GUIDE
    const val EXPERT = TutorialScript.SURVIVAL_EXPERT
    const val LOCKED = "house_door_locked"
    const val GATE_LOCKED = "gate_locked"

    val start = Position(3200, 3200)

    val door = Door(
        closed = listOf(DoorLeaf(3014, Position(3205, 3200), WallSide.WEST)),
        open = listOf(DoorPiece(1535, Position(3204, 3200), rotation = 1)),
        opensAt = OPEN_HOUSE_DOOR,
        firstPass = FIND_SURVIVAL_EXPERT,
        locked = LOCKED,
    )

    val gate = Door(
        closed = listOf(DoorLeaf(3015, Position(3210, 3202), WallSide.EAST), DoorLeaf(3016, Position(3210, 3201), WallSide.EAST)),
        open = listOf(DoorPiece(49, Position(3211, 3202), rotation = 1), DoorPiece(50, Position(3212, 3202), rotation = 1)),
        opensAt = LEAVE_SURVIVAL_AREA,
        firstPass = FIND_MASTER_CHEF,
        locked = GATE_LOCKED,
    )

    private val plainSteps = TutorialStep.entries.filter { it != TutorialStep.DONE }
        .associateWith { StepScreen(HelpBox(it.name, listOf("${it.name} line.")), HintTarget.None, emptyList()) }

    val data = TutorialData(
        start = start,
        steps = plainSteps + mapOf(
            DESIGN_CHARACTER to StepScreen(
                HelpBox("Getting started", listOf("Design your character.", "Then talk to the guide.")),
                HintTarget.Npc(GUIDE),
                listOf(TabIndex.LOGOUT, TabIndex.SETTINGS),
            ),
            TALK_TO_GUIDE to StepScreen(HelpBox("Getting started", listOf("Talk to the guide.")), HintTarget.Npc(GUIDE), emptyList()),
            OPEN_HOUSE_DOOR to StepScreen(
                HelpBox("Doors", listOf("Open the door.")),
                HintTarget.Tile(door.closed.single().tile, TileEdge.WEST, height = 128),
                emptyList(),
            ),
            FIND_SURVIVAL_EXPERT to StepScreen(HelpBox("Moving around", listOf("One.", "Two.", "Three.", "Four.")), HintTarget.Npc(EXPERT), emptyList()),
            OPEN_INVENTORY to StepScreen(HelpBox("Backpack", listOf("Open it.")), HintTarget.None, listOf(TabIndex.INVENTORY), TabIndex.INVENTORY),
            CUT_TREE to StepScreen(HelpBox("Tree", listOf("Chop it.")), HintTarget.None, emptyList()),
            OPEN_SKILLS to StepScreen(HelpBox("Skills", listOf("Open them.")), HintTarget.None, listOf(TabIndex.SKILL), TabIndex.SKILL),
        ),
        dialogues = mapOf(
            TutorialScript.GUIDE_WELCOME to listOf(DialogueBox.Npc(listOf("Welcome.")), DialogueBox.Player(listOf("Thanks."))),
            TutorialScript.GUIDE_AGAIN to listOf(DialogueBox.Npc(listOf("Again."))),
            TutorialScript.SURVIVAL_WELCOME to listOf(DialogueBox.Npc(listOf("I'm the Survival Expert."))),
            TutorialScript.SURVIVAL_INVENTORY to listOf(DialogueBox.Npc(listOf("Open your backpack."))),
            TutorialScript.SURVIVAL_FIRE to listOf(DialogueBox.Npc(listOf("Make a fire."))),
            TutorialScript.SURVIVAL_SKILLS to listOf(DialogueBox.Npc(listOf("Look at your skills."))),
            TutorialScript.SURVIVAL_FOOD to listOf(DialogueBox.Npc(listOf("Take this net.")), DialogueBox.Text(listOf("You get a net."))),
            TutorialScript.SURVIVAL_SHRIMP to listOf(DialogueBox.Npc(listOf("Cook a shrimp."))),
            TutorialScript.SURVIVAL_DONE to listOf(DialogueBox.Npc(listOf("Off you go."))),
            TutorialScript.GIVES_AXE_AND_TINDERBOX to listOf(DialogueBox.Items(listOf(590, 1351), listOf("Axe and tinderbox."))),
            TutorialScript.GIVES_AXE to listOf(DialogueBox.Items(listOf(1351), listOf("An axe."))),
            TutorialScript.GIVES_TINDERBOX to listOf(DialogueBox.Items(listOf(590), listOf("A tinderbox."))),
            TutorialScript.GIVES_NET to listOf(DialogueBox.Items(listOf(303), listOf("A net."))),
            LOCKED to listOf(DialogueBox.Text(listOf("Locked."))),
            GATE_LOCKED to listOf(DialogueBox.Text(listOf("Gate locked."))),
        ),
        doors = listOf(door, gate),
        messages = mapOf(
            LunaTutorial.CANNOT_WIELD to "Not yet.",
            LunaTutorial.SHRIMP_BURNT to "Burnt.",
            LunaTutorial.SHRIMP_COOKED to "Cooked.",
        ),
        busy = mapOf(
            TutorialScript.WOODCUTTING to HelpBox("Please wait...", listOf("Chopping.")),
            TutorialScript.FIREMAKING to HelpBox("Please wait...", listOf("", "Lighting, <he/she> says.")),
            TutorialScript.FISHING to HelpBox("Please wait...", listOf("Fishing.")),
        ),
        quietMessages = setOf("You get some logs."),
    )

    /** A valid `tutorial.jsonc`, with any part replaced. */
    fun json(
        start: String = """{ "x": 3200, "y": 3200 }""",
        steps: String = steps(),
        dialogues: String = DIALOGUES,
        doors: String = "",
        messages: String = "",
        busy: String = "",
        quiet: String = "",
    ) = """{ "start": $start, "steps": { $steps }, "dialogues": { $dialogues }, "doors": [ $doors ], "messages": { $messages },
        "busy": { $busy }, "quiet_messages": [ $quiet ] }"""

    /** Every step with a plain help box, [replaced] ones aside. */
    fun steps(vararg replaced: Pair<TutorialStep, String>): String {
        val byStep = TutorialStep.entries.filter { it != TutorialStep.DONE }.associateWith { """{ "help": $HELP }""" } +
            replaced
        return byStep.entries.joinToString(",") { (step, json) -> "\"${step.name}\": $json" }
    }

    const val HELP = """{ "title": "Title", "lines": ["Line."] }"""

    const val DIALOGUES =
        """"guide_welcome": [ { "npc": ["Welcome."] } ], "guide_again": [ { "npc": ["Again."] } ], "locked": [ { "text": ["Locked."] } ]"""

    const val DOOR = """{
        "closed": [ { "id": 3014, "tile": { "x": 3205, "y": 3200 }, "side": "WEST" } ],
        "open": [ { "id": 1535, "tile": { "x": 3204, "y": 3200 }, "rotation": 1 } ],
        "opens_at": "OPEN_HOUSE_DOOR", "first_pass": "FIND_SURVIVAL_EXPERT", "locked": "locked" }"""
}
