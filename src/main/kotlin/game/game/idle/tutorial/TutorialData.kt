package game.idle.tutorial

import game.idle.ui.TileEdge
import io.luna.game.model.Position
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The help box in the chatbox: a title and one to [MAX_LINES] lines. */
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

/** What a step shows, and the side tabs that appear at that step. */
data class StepScreen(val help: HelpBox, val arrow: HintTarget, val tabs: List<TabIndex>)

/** One box of a dialogue, one to [MAX_LINES] lines. */
sealed interface DialogueBox {
    val lines: List<String>

    data class Npc(override val lines: List<String>) : DialogueBox

    data class Player(override val lines: List<String>) : DialogueBox

    data class Text(override val lines: List<String>) : DialogueBox

    companion object {
        const val MAX_LINES = 4
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

/** A door as the client draws it: which object, on which tile, turned how. */
data class DoorPiece(val id: Int, val position: Position, val rotation: Int)

/**
 * A door on the tutorial's path. Locked below [opensAt] with the [locked] dialogue; the first time through at
 * [opensAt] moves the player on to [firstPass].
 */
data class Door(
    val id: Int,
    val tile: Position,
    val side: WallSide,
    val openId: Int,
    val opensAt: TutorialStep,
    val firstPass: TutorialStep,
    val locked: String,
) {
    /** The tile on the other side of the wall, where the open door is drawn. */
    val across: Position get() = tile.translate(side.dx, side.dy)

    /** An open door is turned a quarter from the closed one. */
    val openRotation: Int get() = (side.rotation + 1) % WallSide.entries.size

    /** Where a player going through from [from] ends up: across the wall when standing on the door's tile. */
    fun destination(from: Position): Position = if (from == tile) across else tile

    /** The piece to take away and the piece to draw, to show the door [open] or shut again. */
    fun change(open: Boolean): Pair<DoorPiece, DoorPiece> {
        val shut = DoorPiece(id, tile, side.rotation)
        val ajar = DoorPiece(openId, across, openRotation)
        return if (open) shut to ajar else ajar to shut
    }
}

/** Everything `tutorial.jsonc` holds. Loaded once at boot from [PATH]; a bad file fails the boot. */
data class TutorialData(
    val start: Position,
    val steps: Map<TutorialStep, StepScreen>,
    val dialogues: Map<String, List<DialogueBox>>,
    val doors: List<Door>,
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
) {
    fun toData(): TutorialData = TutorialData(
        start = requireNotNull(start) { "The tutorial has no start tile" }.toPosition(),
        steps = steps.entries.associate { (name, step) -> TutorialStep.valueOf(name) to step.toScreen(name) },
        dialogues = dialogues.mapValues { (name, boxes) -> boxes.map { it.toBox(name) } },
        doors = doors.map { it.toDoor() },
    )
}

internal data class PositionJson(val x: Int = -1, val y: Int = -1) {
    fun toPosition() = Position(x, y)
}

internal data class StepJson(val help: HelpJson? = null, val arrow: ArrowJson? = null, val tabs: List<String> = emptyList()) {
    fun toScreen(step: String) = StepScreen(
        help = requireNotNull(help) { "Tutorial step $step has no help box" }.toHelp(step),
        arrow = arrow?.toTarget(step) ?: HintTarget.None,
        tabs = tabs.map { TabIndex.valueOf(it) },
    )
}

internal data class HelpJson(val title: String = "", val lines: List<String> = emptyList()) {
    fun toHelp(step: String): HelpBox {
        require(title.isNotBlank()) { "Tutorial step $step has a help box without a title" }
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

internal data class BoxJson(val npc: List<String>? = null, val player: List<String>? = null, val text: List<String>? = null) {
    fun toBox(dialogue: String): DialogueBox {
        val boxes = listOfNotNull(npc?.let(DialogueBox::Npc), player?.let(DialogueBox::Player), text?.let(DialogueBox::Text))
        require(boxes.size == 1) { "A box of dialogue $dialogue needs exactly one of npc, player or text" }
        val box = boxes.single()
        requireLines(box.lines, DialogueBox.MAX_LINES) { "A box of dialogue $dialogue" }
        return box
    }
}

internal data class DoorJson(
    val id: Int = -1,
    val tile: PositionJson? = null,
    val side: String = "",
    val openId: Int = -1,
    val opensAt: String = "",
    val firstPass: String = "",
    val locked: String = "",
) {
    fun toDoor(): Door {
        require(id >= 0) { "A tutorial door has no id" }
        require(openId >= 0) { "Tutorial door $id has no open_id" }
        return Door(
            id = id,
            tile = requireNotNull(tile) { "Tutorial door $id has no tile" }.toPosition(),
            side = WallSide.valueOf(side),
            openId = openId,
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
