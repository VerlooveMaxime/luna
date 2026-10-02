package game.idle.tutorial

import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The help box in the chatbox: a title, possibly blank, and one to [MAX_LINES] lines. */
data class HelpBox(val title: String, val lines: List<String>) {
    companion object {
        const val MAX_LINES = 4
    }
}

/** What the yellow arrow points at. */
sealed interface HintTarget {
    data class Npc(val id: Int) : HintTarget

    data class Tile(val position: Position, val edge: TileEdge, val height: Int) : HintTarget

    data object None : HintTarget
}

/** What a step shows, the side tabs that appear at that step, and the one that flashes until it is clicked. */
data class StepScreen(val help: HelpBox, val arrow: HintTarget, val tabs: List<TabIndex>, val flash: TabIndex? = null)

/** One box of a dialogue, one to [MAX_LINES] lines. */
sealed interface DialogueBox {
    val lines: List<String>

    data class Npc(override val lines: List<String>) : DialogueBox

    data class Player(override val lines: List<String>) : DialogueBox

    data class Text(override val lines: List<String>) : DialogueBox

    /** One or two items shown beside the lines, like an item handed over. */
    data class Items(val items: List<Int>, override val lines: List<String>) : DialogueBox

    companion object {
        const val MAX_LINES = 4
        const val MAX_ITEMS = 2
    }
}

/** The edge of its tile a wall stands on, in the cache's rotation order. */
enum class WallSide(val dx: Int, val dy: Int) {
    WEST(-1, 0),
    NORTH(0, 1),
    EAST(1, 0),
    SOUTH(0, -1),
    ;

    val rotation: Int get() = ordinal
}

/** A door or gate piece as the client draws it: which object, on which tile, turned how. */
data class DoorPiece(val id: Int, val position: Position, val rotation: Int) {
    companion object {
        const val ROTATIONS = 4
    }
}

/** A closed piece of a door or gate, the object players click; it stands on the [side] edge of [tile]. */
data class DoorLeaf(val id: Int, val tile: Position, val side: WallSide) {
    /** The tile on the other side of the wall. */
    val across: Position get() = tile.translate(side.dx, side.dy)

    val piece: DoorPiece get() = DoorPiece(id, tile, side.rotation)

    /** Where a player going through from [from] ends up: across the wall when standing on the leaf's tile. */
    fun destination(from: Position): Position = if (from == tile) across else tile
}

/**
 * A door or gate on the tutorial's path: its [closed] leaves, and the [open] pieces drawn instead while a player goes
 * through. Locked below [opensAt] with the [locked] dialogue; the first time through at [opensAt] moves the player
 * on to [firstPass].
 */
data class Door(
    val closed: List<DoorLeaf>,
    val open: List<DoorPiece>,
    val opensAt: TutorialStep,
    val firstPass: TutorialStep,
    val locked: String,
) {
    /** The pieces to take away and the pieces to draw, to show the door [open] or shut again. */
    fun change(open: Boolean): Pair<List<DoorPiece>, List<DoorPiece>> {
        val shut = closed.map { it.piece }
        return if (open) shut to this.open else this.open to shut
    }
}

/**
 * Everything `tutorial.jsonc` holds. Loaded once at boot from [PATH]; a bad file fails the boot. [busy] holds the
 * help boxes shown while an activity runs, by activity; [quietMessages] the chat lines players on the island never
 * get, because the 2006 island showed a help box instead.
 */
data class TutorialData(
    val start: Position,
    val steps: Map<TutorialStep, StepScreen>,
    val dialogues: Map<String, List<DialogueBox>>,
    val doors: List<Door>,
    val messages: Map<String, String>,
    val busy: Map<String, HelpBox>,
    val quietMessages: Set<String>,
) {
    init {
        val missing = TutorialStep.entries.filter { it != TutorialStep.DONE && it !in steps }
        require(missing.isEmpty()) { "Tutorial steps without a screen: $missing" }
        val unknown = doors.map { it.locked }.filter { it !in dialogues }
        require(unknown.isEmpty()) { "Doors name dialogues that do not exist: $unknown" }
    }

    companion object {
        val PATH: Path = Paths.get("data", "idle", "tutorial.jsonc")

        fun parse(jsonc: String): TutorialData =
            (GsonUtils.GSON.fromJson(jsonc, TutorialJson::class.java) ?: TutorialJson()).toData()

        fun load(path: Path): TutorialData = parse(Files.readString(path))
    }
}

/*
 * Raw Gson shapes. Every field has a default so Gson uses the no-arg constructor and a missing key can never leave a
 * Kotlin non-null field null; the conversions below turn each gap into a message naming where it is.
 */

internal data class TutorialJson(
    val start: PositionJson? = null,
    val steps: Map<String, StepJson> = emptyMap(),
    val dialogues: Map<String, List<BoxJson>> = emptyMap(),
    val doors: List<DoorJson> = emptyList(),
    val messages: Map<String, String> = emptyMap(),
    val busy: Map<String, HelpJson> = emptyMap(),
    val quietMessages: List<String> = emptyList(),
) {
    fun toData(): TutorialData = TutorialData(
        start = requireNotNull(start) { "The tutorial has no start tile" }.toPosition(),
        steps = steps.entries.associate { (name, step) -> TutorialStep.valueOf(name) to step.toScreen(name) },
        dialogues = dialogues.mapValues { (name, boxes) -> boxes.map { it.toBox(name) } },
        doors = doors.mapIndexed { index, door -> door.toDoor(index) },
        messages = messages,
        busy = busy.mapValues { (activity, help) -> help.toHelp("busy $activity") },
        quietMessages = quietMessages.toSet(),
    )
}

internal data class PositionJson(val x: Int = -1, val y: Int = -1) {
    fun toPosition() = Position(x, y)
}

internal data class StepJson(
    val help: HelpJson? = null,
    val arrow: ArrowJson? = null,
    val tabs: List<String> = emptyList(),
    val flash: String? = null,
) {
    fun toScreen(step: String) = StepScreen(
        help = requireNotNull(help) { "Tutorial step $step has no help box" }.toHelp(step),
        arrow = arrow?.toTarget(step) ?: HintTarget.None,
        tabs = tabs.map { TabIndex.valueOf(it) },
        flash = flash?.let { TabIndex.valueOf(it) },
    )
}

internal data class HelpJson(val title: String = "", val lines: List<String> = emptyList()) {
    /** A blank title is allowed: some of the 2006 boxes have none. */
    fun toHelp(step: String): HelpBox {
        requireLines(lines, HelpBox.MAX_LINES) { "The help box of tutorial step $step" }
        return HelpBox(title, lines)
    }
}

internal data class ArrowJson(
    val npc: Int? = null,
    val tile: PositionJson? = null,
    val edge: String = TileEdge.CENTRE.name,
    val height: Int = 0,
) {
    fun toTarget(step: String): HintTarget {
        require(npc == null || tile == null) { "The arrow of tutorial step $step points at an npc and a tile" }
        return npc?.let { HintTarget.Npc(it) } ?: tileTarget(step)
    }

    private fun tileTarget(step: String): HintTarget {
        val position = requireNotNull(tile) { "The arrow of tutorial step $step points at nothing" }.toPosition()
        return HintTarget.Tile(position, TileEdge.valueOf(edge), height)
    }
}

/** A box with [items] is a text box that shows them; items without text are refused. */
internal data class BoxJson(
    val npc: List<String>? = null,
    val player: List<String>? = null,
    val text: List<String>? = null,
    val items: List<Int>? = null,
) {
    fun toBox(dialogue: String): DialogueBox {
        val boxes = listOfNotNull(npc?.let(DialogueBox::Npc), player?.let(DialogueBox::Player), text?.let(::textBox))
        require(boxes.size == 1) { "A box of dialogue $dialogue needs exactly one of npc, player or text" }
        require(items == null || text != null) { "A box of dialogue $dialogue shows items without text" }
        val box = boxes.single()
        requireLines(box.lines, DialogueBox.MAX_LINES) { "A box of dialogue $dialogue" }
        return box
    }

    private fun textBox(lines: List<String>): DialogueBox = items?.let { itemBox(it, lines) } ?: DialogueBox.Text(lines)

    private fun itemBox(ids: List<Int>, lines: List<String>): DialogueBox {
        require(ids.isNotEmpty()) { "An item box shows no items" }
        require(ids.size <= DialogueBox.MAX_ITEMS) { "An item box shows more than ${DialogueBox.MAX_ITEMS} items" }
        return DialogueBox.Items(ids, lines)
    }
}

internal data class LeafJson(val id: Int = -1, val tile: PositionJson? = null, val side: String = "") {
    fun toLeaf(door: Int): DoorLeaf {
        require(id >= 0) { "A closed piece of tutorial door $door has no id" }
        val position = requireNotNull(tile) { "Closed piece $id of tutorial door $door has no tile" }.toPosition()
        return DoorLeaf(id, position, WallSide.valueOf(side))
    }
}

internal data class PieceJson(val id: Int = -1, val tile: PositionJson? = null, val rotation: Int = -1) {
    fun toPiece(door: Int): DoorPiece {
        require(id >= 0) { "An open piece of tutorial door $door has no id" }
        require(rotation >= 0) { "Open piece $id of tutorial door $door has no rotation" }
        require(rotation < DoorPiece.ROTATIONS) { "Open piece $id of tutorial door $door turns past ${DoorPiece.ROTATIONS - 1}" }
        val position = requireNotNull(tile) { "Open piece $id of tutorial door $door has no tile" }.toPosition()
        return DoorPiece(id, position, rotation)
    }
}

internal data class DoorJson(
    val closed: List<LeafJson> = emptyList(),
    val open: List<PieceJson> = emptyList(),
    val opensAt: String = "",
    val firstPass: String = "",
    val locked: String = "",
) {
    /** Doors are named by their place in the file, counting from 1. */
    fun toDoor(index: Int): Door {
        val door = index + 1
        require(closed.isNotEmpty()) { "Tutorial door $door has no closed pieces" }
        require(open.isNotEmpty()) { "Tutorial door $door has no open pieces" }
        return Door(
            closed = closed.map { it.toLeaf(door) },
            open = open.map { it.toPiece(door) },
            opensAt = TutorialStep.valueOf(opensAt),
            firstPass = TutorialStep.valueOf(firstPass),
            locked = locked,
        )
    }
}

private fun requireLines(lines: List<String>, max: Int, where: () -> String) {
    require(lines.isNotEmpty()) { "${where()} has no lines" }
    require(lines.size <= max) { "${where()} has more than $max lines" }
}
