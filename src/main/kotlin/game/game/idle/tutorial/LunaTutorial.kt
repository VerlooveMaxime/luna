package game.idle.tutorial

import api.predef.ext.scheduleOnce
import game.idle.idleState
import game.idle.ui.HintArrowMessageWriter
import game.idle.ui.StickyChatboxMessageWriter
import game.player.login.firstLogin
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableMessage
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.AddObjectMessageWriter
import io.luna.net.msg.out.GroupedEntityMessageWriter
import io.luna.net.msg.out.RemoveObjectMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import java.util.ArrayDeque

/** The tutorial's progress on a player, kept in their idle state. */
var Player.tutorialStep: TutorialStep
    get() = TutorialStep.of(idleState.tutorialStep)
    set(step) {
        idleState = idleState.copy(tutorialStep = step.value)
    }

/** Keeps a player on the tutorial while it lasts; Luna calls [process] every tick. */
class TutorialController(private val player: Player, private val tutorial: LunaTutorial) : PlayerController(player) {
    override fun process() = tutorial.checkDesigner(player)
}

/**
 * Tutorial Island on real players: starts new characters there, shows each step (side tabs, help box, arrow),
 * opens the instructors' dialogues and lets players through the doors.
 */
class LunaTutorial(private val script: TutorialScript, private val data: TutorialData, private val world: World) {

    fun onLogin(player: Player) {
        if (player.firstLogin) {
            player.firstLogin = false
            player.tutorialStep = TutorialStep.DESIGN_CHARACTER
        }
        if (player.tutorialStep != TutorialStep.DONE) {
            resume(player)
        }
    }

    /** A character that logged out in the designer starts again in the guide's house, like a new one. */
    private fun resume(player: Player) {
        if (player.tutorialStep == TutorialStep.DESIGN_CHARACTER) {
            player.move(data.start)
            player.overlays.open(DesignPlayerInterface())
        }
        player.controllers.register(TutorialController(player, this))
        // Luna's own login listeners send every side tab; ours go out after them.
        world.scheduleOnce(1) { show(player, player.tutorialStep) }
    }

    fun checkDesigner(player: Player) {
        if (!player.overlays.has(DesignPlayerInterface::class.java)) {
            advance(player, script.designerClosed(player.tutorialStep))
        }
    }

    fun talkToGuide(player: Player, guide: Npc) {
        val talk = script.talkToGuide(player.tutorialStep)
        openDialogue(player, talk.dialogue, speaker = guide.id) { advance(player, talk.advanceTo) }
    }

    fun openDoor(player: Player, door: Door) = when (val outcome = script.openDoor(door, player.tutorialStep)) {
        is DoorOutcome.Locked -> openDialogue(player, outcome.dialogue, speaker = NO_SPEAKER) {}
        is DoorOutcome.Pass -> {
            goThrough(player, door)
            advance(player, outcome.advanceTo)
        }
    }

    private fun advance(player: Player, step: TutorialStep?) {
        if (step != null) {
            player.tutorialStep = step
            show(player, step)
        }
    }

    private fun show(player: Player, step: TutorialStep) {
        val screen = script.screen(step)
        TabIndex.values().forEach { showTab(player, it, visible = it in screen.tabs) }
        showHelp(player, screen.help)
        player.queue(arrow(player, screen.arrow))
    }

    private fun showTab(player: Player, tab: TabIndex, visible: Boolean) =
        if (visible) player.tabs.reset(tab) else player.tabs.clear(tab)

    /** Texts go first: the client only redraws the chatbox when the box itself arrives. */
    private fun showHelp(player: Player, help: HelpBox) {
        val lines = help.lines + List(HelpBox.MAX_LINES - help.lines.size) { "" }
        player.queue(WidgetTextMessageWriter(help.title, HELP_TITLE))
        lines.zip(HELP_LINES).forEach { (text, id) -> player.queue(WidgetTextMessageWriter(text, id)) }
        player.queue(StickyChatboxMessageWriter(HELP_BOX))
    }

    private fun arrow(player: Player, target: HintTarget): HintArrowMessageWriter = when (target) {
        is HintTarget.Npc -> nearestNpc(player, target.id)?.let { HintArrowMessageWriter.overNpc(it.index) }
            ?: HintArrowMessageWriter.hidden()
        is HintTarget.Tile -> HintArrowMessageWriter.overTile(target.position, target.edge, target.height)
        HintTarget.None -> HintArrowMessageWriter.hidden()
    }

    private fun nearestNpc(player: Player, id: Int): Npc? =
        world.npcs.findAll { it.id == id }.minByOrNull { it.position.computeLongestDistance(player.position) }

    private fun openDialogue(player: Player, name: String, speaker: Int, then: () -> Unit) {
        val dialogue = data.dialogues.getValue(name).fold(player.newDialogue()) { builder, box -> add(builder, box, speaker) }
        dialogue.then { then() }.open()
    }

    private fun add(builder: DialogueQueueBuilder, box: DialogueBox, speaker: Int): DialogueQueueBuilder = when (box) {
        is DialogueBox.Npc -> builder.npc(speaker, *box.lines.toTypedArray())
        is DialogueBox.Player -> builder.player(*box.lines.toTypedArray())
        is DialogueBox.Text -> builder.text(*box.lines.toTypedArray())
    }

    /** The server's door never opens: only [player] sees it open, and is walked through it. */
    private fun goThrough(player: Player, door: Door) {
        showDoor(player, door, open = true)
        player.walking.clear()
        player.walking.replacePath(ArrayDeque(listOf(door.destination(player.position))))
        world.scheduleOnce(DOOR_OPEN_TICKS) { showDoor(player, door, open = false) }
    }

    private fun showDoor(player: Player, door: Door, open: Boolean) {
        val (hidden, shown) = door.change(open)
        player.queue(inChunk(player, hidden.position) { RemoveObjectMessageWriter(WALL, hidden.rotation, it) })
        player.queue(inChunk(player, shown.position) { AddObjectMessageWriter(shown.id, WALL, shown.rotation, it) })
    }

    private fun inChunk(player: Player, position: Position, change: (Int) -> ChunkUpdatableMessage): GroupedEntityMessageWriter {
        val chunk = world.chunks.load(position)
        return GroupedEntityMessageWriter(player.lastRegion, chunk, listOf(change(chunk.chunk.offset(position))))
    }

    companion object {
        const val HELP_BOX = 6179
        const val HELP_TITLE = 6180
        val HELP_LINES = listOf(6181, 6182, 6183, 6184)
        const val DOOR_OPEN_TICKS = 3
        private const val NO_SPEAKER = -1
        private val WALL = ObjectType.STRAIGHT_WALL.id
    }
}
