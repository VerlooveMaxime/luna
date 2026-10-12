package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.ui.ClientFont
import io.luna.game.model.mob.Skill

/**
 * Every line a running flow says to the player, from the typed reasons the steps report (Maxime, 2026-10-11, one rule
 * for all 14 kinds). A block is a fragment the slot, the overlay and the configure screen show as is: sentence case, no
 * period, never addressing the player ("you can use" after a tool with levels is Maxime's one wording with it), the
 * bag never "inventory", the thing named only when the slot does not (the slot already names the kind and what it
 * works on), on at most [SLOT_LINES] of the slot's lines. A chat line is a sentence naming the step by its number:
 * "Autopilot: step N ended, <cause>." and "Autopilot: step N skipped this lap: <cause>.", "Autopilot: stopped,
 * <cause>." for every stop. Items are named with [names]; a list of them is cut to the chat box's width in its
 * [chat] font.
 */
class StepWords(private val names: GameNames, private val chat: ClientFont) {

    /** Why a step cannot go on, as the red line under it. */
    fun block(reason: Reason): String =
        when (reason) {
            is Reason.NeedsTool -> needs(reason.tool)
            is Reason.NeedsLevel -> "Needs ${Skill.getName(reason.skill)} ${reason.level}"
            is Reason.NoneInReach -> noneInReach(reason.target)
            Reason.CannotMake -> "Cannot make it yet"
            Reason.WrongWindow -> "Another make window is open"
            is Reason.OtherFloor -> otherFloor(reason.place)
            is Reason.NoWay -> noWay(reason.place)
            is Reason.BagFull -> "The bag is full"
            is Reason.NoneLeftInBank -> "No ${item(reason.item)} left in the bank"
            Reason.BankFull -> "The bank is full"
        }

    /** What step [number] tells the player: "Autopilot: step 2 ended, the bag is full: bones, cowhide left lying." */
    fun said(number: Int, said: Said): String =
        when (said) {
            is Said.Ended -> line("${PREFIX}step $number ended, ", said.reason)
            is Said.Skipped -> line("${PREFIX}step $number skipped this lap: ", said.reason)
        }

    /** Why the flow stopped at step [number]. */
    fun stop(number: Int, stop: StepStop): String =
        when (stop) {
            is StepStop.Stuck -> line("${PREFIX}stopped, step $number is stuck: ", stop.reason)
            is StepStop.Reflex -> "${PREFIX}stopped, reflex ${stop.number} ran at ${stop.health.text()} hitpoints."
        }

    fun cutShort(number: Int): String = "${PREFIX}stopped, step $number was cut short again before it made progress."

    /** The reflex at [number] ran away at [health] and the flow goes on from step [toStep]. */
    fun jump(number: Int, health: Health, toStep: Int): String =
        "${PREFIX}reflex $number ran at ${health.text()} hitpoints, jumping to step $toStep."

    /** [start] then the reason as a cause and a period, the whole line fitting the chat box. */
    private fun line(start: String, reason: Reason.Stuck): String = "$start${cause(reason, CHAT_ROOM - chat.width("$start."))}."

    /**
     * The reason as the cause of a sentence, in [room] pixels: the block's words in lower case, a full bag listing what
     * it left, each name once (noted and unnoted bones are one name).
     */
    private fun cause(reason: Reason.Stuck, room: Int): String {
        val left = if (reason is Reason.BagFull) reason.left else emptyList()
        if (left.isEmpty()) return block(reason).let { it.take(1).lowercase() + it.drop(1) }
        val head = "the bag is full: "
        val tail = " left lying"
        return head + chat.fit(left.map(::item).distinct().joinToString(", "), room - chat.width(head + tail)) + tail
    }

    /** "Needs an axe you can use" for a tool with levels, "Needs a tinderbox" for one without, "Needs fishing bait" for bait. */
    private fun needs(tool: ToolNeed): String {
        val word = tool.word ?: item(tool.items.keys.first())
        val usable = if (tool.skill == null) "" else " you can use"
        return if (tool.countable) "Needs ${article(word)} $word$usable" else "Needs $word"
    }

    private fun noneInReach(target: Target): String =
        when (target) {
            Target.TREE -> "No tree in reach"
            Target.ROCK -> "No rock in reach"
            Target.FISHING_SPOT -> "No fishing spot in reach"
            Target.FIRE_OR_RANGE -> "No fire or range in reach"
            Target.FURNACE -> "No furnace in reach"
            Target.ANVIL -> "No anvil in reach"
            Target.NPC -> "Nothing to fight in reach"
            Target.BANK_BOOTH -> "No bank booth on this floor"
            Target.FREE_TILE -> "No free tile for a fire"
        }

    private fun otherFloor(place: Place): String =
        when (place) {
            Place.TILE -> "Cannot walk to another floor"
            Place.WORK_SPOT -> "The work spot is on another floor"
        }

    private fun noWay(place: Place): String =
        when (place) {
            Place.TILE -> "No way there from here"
            Place.WORK_SPOT -> "No way to the work spot from here"
        }

    private fun item(id: Int): String = names.item(id).lowercase()

    private fun article(word: String): String = if (word.first() in VOWELS) "an" else "a"

    companion object {
        /** The chat box draws from x 4 and clips at 463 px (`client.java`), so a line has this much room. */
        const val CHAT_ROOM = 459

        /** How many of the slot's lines a block may take (Maxime, 2026-10-11): the rest keep the target and a detail. */
        const val SLOT_LINES = 2

        private const val PREFIX = "Autopilot: "

        private const val VOWELS = "aeiou"

        const val DIED = "${PREFIX}stopped, you died."

        /** A whole lap went by with no step working (Maxime, 2026-10-11). */
        const val IDLE_LAP = "${PREFIX}stopped, no step had anything to do."

        const val CANNOT_RESUME = "${PREFIX}could not resume your flow. Check it in the flow builder."

        /**
         * The chat line for step [number] when it cannot work, at Run or while running: the reason is on the step's
         * slot, and in full it is often too long for a chat line (Maxime, 2026-10-09).
         */
        fun cannotWork(number: Int): String = "${PREFIX}step $number cannot work yet. See its slot warning in the builder."
    }
}
