package game.idle.tutorial

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.scheduleOnce
import game.idle.idleState
import game.idle.ui.HintArrowMessageWriter
import game.idle.ui.IdleUi
import game.idle.ui.StickyChatboxMessageWriter
import game.player.login.firstLogin
import game.skill.firemaking.LightAction
import game.skill.fishing.catchFish.CatchFishAction
import game.skill.mining.mineOre.MineOreAction
import game.skill.smithing.smithBar.SmithingInterface
import game.skill.woodcutting.cutTree.CutTreeAction
import io.luna.game.event.impl.ControllableEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.InteractableEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.action.Action
import io.luna.game.action.ActionState
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableMessage
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.interact.InteractionAction
import io.luna.game.model.mob.interact.InteractionActionListener
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectType
import io.luna.net.msg.out.AddObjectMessageWriter
import io.luna.net.msg.out.FlashTabMessageWriter
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

/** The activity whose "please wait" box the help box shows, or blank for the step's own box. Not saved. */
private var Player.tutorialBusy by Attr.string()

/** Keeps a player on the tutorial while it lasts: Luna asks it before every click and calls [process] every tick. */
class TutorialController(private val player: Player, private val tutorial: LunaTutorial) : PlayerController(player) {
    override fun process() {
        tutorial.checkDesigner(player)
        tutorial.checkBusy(player)
        tutorial.checkGoal(player)
    }

    override fun event(event: ControllableEvent): Boolean = tutorial.allows(player, event)
}

/**
 * Tutorial Island on real players: starts new characters there, shows each step (side tabs, help box, arrow), opens
 * the instructors' dialogues, hands out their tools, follows the player's first log, fire and shrimp, scripts the
 * first two shrimp cooked, lets players through the doors and up or down the ladders at their steps, and keeps the
 * island's line in the quest journal's stages.
 */
class LunaTutorial(private val script: TutorialScript, private val data: TutorialData, private val world: World) {

    init {
        val missing = MESSAGES.filter { it !in data.messages }
        require(missing.isEmpty()) { "The tutorial needs messages that are missing: $missing" }
    }

    fun onLogin(player: Player) {
        if (player.firstLogin) {
            player.firstLogin = false
            player.tutorialStep = TutorialStep.DESIGN_CHARACTER
        }
        if (player.tutorialStep != TutorialStep.DONE) {
            resume(player)
        } else {
            showJournalLine(player)
        }
    }

    /** A character that logged out in the designer starts again in the guide's house, like a new one. */
    private fun resume(player: Player) {
        if (player.tutorialStep == TutorialStep.DESIGN_CHARACTER) {
            player.move(data.start)
            player.overlays.open(DesignPlayerInterface())
        }
        player.controllers.register(TutorialController(player, this))
        player.setMessageFilter { !script.quiet(it) }
        // Luna's own login listeners send every side tab; ours go out after them.
        world.scheduleOnce(1) { show(player, player.tutorialStep) }
    }

    fun checkDesigner(player: Player) {
        if (!player.overlays.has(DesignPlayerInterface::class.java)) {
            advance(player, script.designerClosed(player.tutorialStep))
        }
    }

    /**
     * While the player chops, lights or fishes, the help box says to wait, as the 2006 island did; not while their
     * autopilot does it, when the help box carries the lesson.
     */
    fun checkBusy(player: Player) {
        val activity = if (player.idleState.running) {
            ""
        } else {
            BUSY_ACTIONS.entries.firstOrNull { (_, type) -> running(player, type) }?.key.orEmpty()
        }
        if (activity != player.tutorialBusy) {
            player.tutorialBusy = activity
            showHelp(player, if (activity.isEmpty()) script.screen(player.tutorialStep).help else busyHelp(player, activity))
        }
    }

    /** A step with a goal moves on once the player does what it asks: their autopilot, what they carry, running. */
    fun checkGoal(player: Player) {
        val state = player.idleState
        val flow = FlowProgress(state.running, state.flow.map(StepSummary::of), state.laps)
        val carried = (0 until player.inventory.capacity()).mapNotNull { player.inventory[it]?.id }.toSet()
        val step = player.tutorialStep
        advance(player, script.goalProgress(step, PlayerProgress(flow, carried, player.isRunning)) ?: script.oreProgress(step, carried))
    }

    /** An interrupted action stays queued until Luna's next pass over the queue, which comes after this check. */
    private fun running(player: Player, type: Class<out Action<*>>): Boolean =
        player.actions.getAll(type).any { it.state == ActionState.PROCESSING }

    private fun busyHelp(player: Player, activity: String): HelpBox {
        val help = script.busyHelp(activity)
        val pronoun = if (player.appearance.isFemale) "she" else "he"
        return help.copy(lines = help.lines.map { it.replace(PRONOUN, pronoun) })
    }

    fun allows(player: Player, event: ControllableEvent): Boolean = when (event) {
        is EquipItemEvent -> mayWield(player)
        is ItemOnObjectEvent -> !cookedByScript(player, event) && !refusedUse(player, event)
        is ObjectClickEvent -> !refusedAtLadder(player, event) && !answeredByScript(player, event)
        is WidgetItemClickEvent -> maySmith(player, event)
        else -> true
    }

    fun talkToGuide(player: Player, guide: Npc) = talk(player, guide, script.talkToGuide(player.tutorialStep))

    /** Lost ingredients are handed back as soon as he is talked to, and a box after his lines shows them. */
    fun talkToChef(player: Player, chef: Npc) {
        val step = player.tutorialStep
        val carried = (0 until player.inventory.capacity()).mapNotNull { player.inventory[it]?.id }.toSet()
        val given = script.ingredients(step, carried)
        give(player, given)
        talk(player, chef, script.talkToChef(step), boxes = script.ingredientBoxes(given))
    }

    /** Lost tools are handed back as soon as she is talked to, and boxes after her lines show them. */
    fun talkToSurvivalExpert(player: Player, expert: Npc) {
        val step = player.tutorialStep
        val given = script.tools(step).filterNot { owns(player, it) }
        give(player, given)
        talk(player, expert, script.talkToSurvivalExpert(step), boxes = script.toolBoxes(given))
    }

    fun tabOpened(player: Player, tab: TabIndex) {
        script.tabOpened(player.tutorialStep, tab)?.let { makeProgress(player, it) }
    }

    fun experienceChanged(player: Player, event: SkillChangeEvent) {
        if (player.skills.getSkill(event.id).experience > event.oldExp) {
            advance(player, script.experienceGained(player.tutorialStep, event.id))
        }
    }

    /**
     * A developer's shortcut for walking through the lessons: moves a player still on the island to the step [name]
     * names, without its items. Returns what to tell them.
     */
    fun jumpTo(player: Player, name: String): String {
        val step = TutorialStep.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return "No tutorial step '$name'."
        if (player.tutorialStep == TutorialStep.DONE || step == TutorialStep.DONE) {
            return "The jump only moves a player still on the island to another step on it."
        }
        advance(player, step)
        return "Tutorial step: ${step.name}"
    }

    fun talkToQuestGuide(player: Player, guide: Npc) = talk(player, guide, script.talkToQuestGuide(player.tutorialStep))

    /** Lost tools are handed back as soon as Dezzick is talked to, and boxes after his lines show them. */
    fun talkToMiningInstructor(player: Player, instructor: Npc) {
        val step = player.tutorialStep
        val given = script.miningTools(step).filterNot { owns(player, it) }
        give(player, given)
        talk(player, instructor, script.talkToMiningInstructor(step), boxes = script.miningToolBoxes(given))
    }

    /** Luna's own ladder handler does the climbing; the first climb at the ladder's step moves the player on. */
    fun ladderClimbed(player: Player, ladder: Ladder) {
        val outcome = script.climbLadder(ladder, player.tutorialStep)
        if (outcome is PassageOutcome.Pass) {
            advance(player, outcome.advanceTo)
        }
    }

    fun openDoor(player: Player, door: Door, leaf: DoorLeaf) = when (val outcome = script.openDoor(door, player.tutorialStep)) {
        is PassageOutcome.Locked -> openDialogue(player, listOf(outcome.dialogue), speaker = NO_SPEAKER) {}
        is PassageOutcome.Pass -> {
            goThrough(player, door, leaf)
            advance(player, outcome.advanceTo)
        }
    }

    private fun talk(player: Player, npc: Npc, talk: Talk, boxes: List<String> = emptyList()) =
        openDialogue(player, talk.dialogues + boxes, speaker = npc.id) {
            talk.progress?.let { makeProgress(player, it) }
        }

    private fun makeProgress(player: Player, progress: Progress) {
        give(player, progress.items)
        advance(player, progress.step)
    }

    private fun advance(player: Player, step: TutorialStep?) {
        if (step != null) {
            player.tutorialStep = step
            show(player, step)
        }
    }

    private fun owns(player: Player, id: Int): Boolean =
        player.inventory.computeAmountForId(id) > 0 || player.equipment.computeAmountForId(id) > 0

    /** An item that does not fit drops at the player's feet, for them only. */
    private fun give(player: Player, ids: List<Int>) = ids.forEach { id ->
        if (!player.inventory.add(Item(id))) {
            world.items.register(GroundItem(world.context, id, 1, player.position, ChunkUpdatableView.localView(player)))
        }
    }

    private fun mayWield(player: Player): Boolean {
        val allowed = script.mayWield(player.tutorialStep)
        if (!allowed) {
            player.sendMessage(data.messages.getValue(CANNOT_WIELD))
        }
        return allowed
    }

    /** The first shrimp and the first bread are the instructors' lessons, not a roll of Luna's cooking. */
    private fun cookedByScript(player: Player, event: ItemOnObjectEvent): Boolean {
        val cook = script.scriptedCook(player.tutorialStep)?.takeIf { event.usedItemId == it.raw && event.objectId in it.places }
        cook?.let { scripted -> onArrival(player, event.gameObject, event) { finishCooking(player, scripted) } }
        return cook != null
    }

    /**
     * A ladder refused at the player's step is not climbed: they walk up to it and its [Ladder.speaker] says why, as
     * the click's own walk would have brought them there.
     */
    private fun refusedAtLadder(player: Player, event: ObjectClickEvent): Boolean {
        val ladder = data.ladders.firstOrNull { it.id == event.gameObject.id } ?: return false
        val refusal = script.climbLadder(ladder, player.tutorialStep) as? PassageOutcome.Locked ?: return false
        onArrival(player, event.gameObject, event) { openDialogue(player, listOf(refusal.dialogue), speaker = ladder.speaker) {} }
        return true
    }

    /**
     * Clicks the island answers itself once the player stands beside the object: prospecting the copper and tin rocks
     * (always, as LostCity scripts it), and the boxes for rocks, the furnace and the anvils before their lessons.
     */
    private fun answeredByScript(player: Player, event: ObjectClickEvent): Boolean {
        val target = event.gameObject
        if (event is ObjectSecondClickEvent && target.id in TutorialScript.ROCKS) {
            onArrival(player, target, event) { player.submitAction(TutorialProspectAction(player) { prospected(player, target.id) }) }
            return true
        }
        val box = script.objectClicked(player.tutorialStep, target.id, firstOption = event is ObjectFirstClickEvent) ?: return false
        onArrival(player, target, event) { openDialogue(player, listOf(box), speaker = NO_SPEAKER) {} }
        return true
    }

    private fun refusedUse(player: Player, event: ItemOnObjectEvent): Boolean {
        val box = script.itemUsedOn(player.tutorialStep, event.objectId) ?: return false
        onArrival(player, event.gameObject, event) { openDialogue(player, listOf(box), speaker = NO_SPEAKER) {} }
        return true
    }

    /** Only the anvil's window is limited: on the island it makes the bronze dagger and nothing else. */
    private fun maySmith(player: Player, event: WidgetItemClickEvent): Boolean {
        if (!player.overlays.has(SmithingInterface::class.java) || script.maySmith(player.tutorialStep, event.itemId)) {
            return true
        }
        openDialogue(player, listOf(TutorialScript.ISLAND_DAGGER_ONLY), speaker = NO_SPEAKER) {}
        return false
    }

    private fun prospected(player: Player, rock: Int) {
        advance(player, script.prospected(player.tutorialStep, rock))
        openDialogue(player, listOf(script.prospectResult(rock)), speaker = NO_SPEAKER) {}
    }

    /** Runs [then] once [player] reaches [target], as the click's own walk would have brought them there. */
    private fun onArrival(player: Player, target: GameObject, event: InteractableEvent, then: () -> Unit) {
        val listener = InteractionActionListener(InteractionPolicy.STANDARD_SIZE) { then() }
        // The action removes each listener it runs, so the list must be mutable.
        player.submitAction(InteractionAction(player, mutableListOf(listener), target, event))
    }

    /** A burn grants no experience: Luna ignores a gain of zero. */
    private fun finishCooking(player: Player, cook: ScriptedCook) {
        if (player.inventory.remove(Item(cook.raw))) {
            player.animation(Animation(cook.animation))
            player.inventory.add(Item(cook.result))
            player.skills.getSkill(Skill.COOKING).addExperience(cook.experience)
            cook.message?.let { player.sendMessage(data.messages.getValue(it)) }
            advance(player, cook.advanceTo)
        }
    }

    private fun show(player: Player, step: TutorialStep) {
        val screen = script.screen(step)
        player.tutorialBusy = ""
        TabIndex.values().forEach { showTab(player, it, visible = it in screen.tabs) }
        showJournalLine(player)
        screen.flash?.let { player.queue(FlashTabMessageWriter(it)) }
        showHelp(player, screen.help)
        player.queue(arrow(player, screen.arrow))
    }

    /**
     * The client drops every widget of a side tab taken away, the journal's line with them, so the line goes out
     * again after the tabs.
     */
    private fun showJournalLine(player: Player) =
        player.queue(WidgetTextMessageWriter(script.journalLine(player.tutorialStep), TutorialScript.JOURNAL_LINE))

    /** The Idle tab sits in the unused slot 7, which Luna's own tab reset leaves empty. */
    private fun showTab(player: Player, tab: TabIndex, visible: Boolean) = when {
        !visible -> player.tabs.clear(tab)
        tab == TabIndex.UNUSED -> IdleUi.installTab(player, player.idleState)
        else -> player.tabs.reset(tab)
    }

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

    private fun openDialogue(player: Player, names: List<String>, speaker: Int, then: () -> Unit) {
        val boxes = names.flatMap { data.dialogues.getValue(it) }
        val name = script.spokenName(player.username)
        val dialogue = boxes.fold(player.newDialogue()) { builder, box -> add(builder, box, speaker, name) }
        dialogue.then { then() }.open()
    }

    private fun add(builder: DialogueQueueBuilder, box: DialogueBox, speaker: Int, name: String): DialogueQueueBuilder {
        val lines = box.lines.map { it.replace(TutorialScript.DISPLAY_NAME, name) }.toTypedArray()
        return when (box) {
            is DialogueBox.Npc -> builder.npc(speaker, *lines)
            is DialogueBox.Player -> builder.player(*lines)
            is DialogueBox.Text -> builder.text(*lines)
            is DialogueBox.Items -> builder.add(ItemBox(box.items, lines.toList()))
        }
    }

    /** The server's door never opens: only [player] sees it open, and is walked through it. */
    private fun goThrough(player: Player, door: Door, leaf: DoorLeaf) {
        showDoor(player, door, open = true)
        player.walking.clear()
        player.walking.replacePath(ArrayDeque(listOf(leaf.destination(player.position))))
        world.scheduleOnce(DOOR_OPEN_TICKS) { showDoor(player, door, open = false) }
    }

    private fun showDoor(player: Player, door: Door, open: Boolean) {
        val (hidden, shown) = door.change(open)
        hidden.forEach { piece ->
            player.queue(inChunk(player, piece.position) { RemoveObjectMessageWriter(WALL, piece.rotation, it) })
        }
        shown.forEach { piece ->
            player.queue(inChunk(player, piece.position) { AddObjectMessageWriter(piece.id, WALL, piece.rotation, it) })
        }
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
        const val CANNOT_WIELD = "cannot_wield"
        private val MESSAGES = listOf(CANNOT_WIELD, TutorialScript.SHRIMP_BURNT, TutorialScript.SHRIMP_COOKED)

        /** Stands for "he" or "she" in a help box, after the player's character. */
        const val PRONOUN = "<he/she>"

        private val BUSY_ACTIONS: Map<String, Class<out Action<*>>> = mapOf(
            TutorialScript.WOODCUTTING to CutTreeAction::class.java,
            TutorialScript.FIREMAKING to LightAction::class.java,
            TutorialScript.FISHING to CatchFishAction::class.java,
            TutorialScript.MINING to MineOreAction::class.java,
            TutorialScript.PROSPECTING to TutorialProspectAction::class.java,
        )
        private const val NO_SPEAKER = -1
        private val WALL = ObjectType.STRAIGHT_WALL.id
    }
}
