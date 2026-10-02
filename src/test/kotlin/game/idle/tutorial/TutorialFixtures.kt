package game.idle.tutorial

import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/** A small island inside `TestWorld`'s open map, shaped like `tutorial.jsonc`. */
object TutorialFixtures {

    const val GUIDE = TutorialScript.RUNESCAPE_GUIDE
    const val LOCKED = "house_door_locked"

    val start = Position(3200, 3200)

    val door = Door(
        id = 3014,
        tile = Position(3205, 3200),
        side = WallSide.WEST,
        openId = 1535,
        opensAt = OPEN_HOUSE_DOOR,
        firstPass = FIND_SURVIVAL_EXPERT,
        locked = LOCKED,
    )

    val data = TutorialData(
        start = start,
        steps = mapOf(
            DESIGN_CHARACTER to StepScreen(
                HelpBox("Getting started", listOf("Design your character.", "Then talk to the guide.")),
                HintTarget.Npc(GUIDE),
                listOf(TabIndex.LOGOUT, TabIndex.SETTINGS),
            ),
            TALK_TO_GUIDE to StepScreen(HelpBox("Getting started", listOf("Talk to the guide.")), HintTarget.Npc(GUIDE), emptyList()),
            OPEN_HOUSE_DOOR to StepScreen(
                HelpBox("Doors", listOf("Open the door.")),
                HintTarget.Tile(door.tile, TileEdge.WEST, height = 128),
                emptyList(),
            ),
            FIND_SURVIVAL_EXPERT to StepScreen(
                HelpBox("Moving around", listOf("One.", "Two.", "Three.", "Four.")),
                HintTarget.None,
                listOf(TabIndex.INVENTORY),
            ),
        ),
        dialogues = mapOf(
            TutorialScript.GUIDE_WELCOME to listOf(DialogueBox.Npc(listOf("Welcome.")), DialogueBox.Player(listOf("Thanks."))),
            TutorialScript.GUIDE_AGAIN to listOf(DialogueBox.Npc(listOf("Again."))),
            LOCKED to listOf(DialogueBox.Text(listOf("Locked."))),
        ),
        doors = listOf(door),
    )

    /** A valid `tutorial.jsonc`, with any part replaced. */
    fun json(
        start: String = """{ "x": 3200, "y": 3200 }""",
        steps: String = steps(),
        dialogues: String = DIALOGUES,
        doors: String = "",
    ) = """{ "start": $start, "steps": { $steps }, "dialogues": { $dialogues }, "doors": [ $doors ] }"""

    /** Every step with a plain help box, [replaced] ones aside. */
    fun steps(vararg replaced: Pair<TutorialStep, String>): String {
        val byStep = TutorialStep.entries.filter { it != TutorialStep.DONE }.associateWith { """{ "help": $HELP }""" } +
            replaced
        return byStep.entries.joinToString(",") { (step, json) -> "\"${step.name}\": $json" }
    }

    const val HELP = """{ "title": "Title", "lines": ["Line."] }"""

    const val DIALOGUES =
        """"guide_welcome": [ { "npc": ["Welcome."] } ], "guide_again": [ { "npc": ["Again."] } ], "locked": [ { "text": ["Locked."] } ]"""

    const val DOOR =
        """{ "id": 3014, "tile": { "x": 3205, "y": 3200 }, "side": "WEST", "open_id": 1535, "opens_at": "OPEN_HOUSE_DOOR", "first_pass": "FIND_SURVIVAL_EXPERT", "locked": "locked" }"""
}
