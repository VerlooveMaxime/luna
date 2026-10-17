package game.idle.flow

import game.idle.flow.option.GameNames
import game.idle.font.ClientFont
import game.idle.location.Tile
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
            is Reason.Impassable -> impassable(reason.passage, reason.tile)
            is Reason.BagFull -> "The bag is full"
            is Reason.NoneLeftInBank -> "No ${item(reason.item)} left in the bank"
            Reason.BankFull -> "The bank is full"
            is Reason.RunningClear -> reason.toStep?.let { "Running clear to jump to step $it" } ?: "Running clear before stopping"
        }

    /** What a trip does on its way, as the white line on the overlay and the running slot: "Opening the door". */
    fun underWay(underWay: UnderWay): String =
        when (underWay) {
            is UnderWay.Opening -> "Opening the ${noun(underWay.passage)}"
            is UnderWay.Passing -> when (underWay.passage.way) {
                Way.THROUGH -> "Going through the ${noun(underWay.passage)}"
                Way.UP -> "Climbing up the ${noun(underWay.passage)}"
                Way.DOWN -> "Climbing down the ${noun(underWay.passage)}"
            }
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
            is StepStop.Reflex -> "${PREFIX}stopped, reflex ${stop.number} fired ${fired(stop.fired)}."
        }

    fun cutShort(number: Int): String = "${PREFIX}stopped, step $number was cut short again before it made progress."

    /** Step [number] met its [goal] and is skipped for the rest of the run (S12). */
    fun goalMet(number: Int, goal: Goal): String = goalLine("${PREFIX}step $number met its goal, ", goal, ": skipped for this run.")

    /** Step [number] met its [goal], which stops the flow. */
    fun goalStops(number: Int, goal: Goal): String = goalLine("${PREFIX}stopped, step $number met its goal: ", goal, ".")

    fun stoppedAfterLaps(laps: Int): String = "${PREFIX}stopped after $laps ${if (laps == 1) "lap" else "laps"}."

    fun stoppedAfterMinutes(minutes: Int): String = "${PREFIX}stopped after $minutes ${if (minutes == 1) "minute" else "minutes"}."

    /** [start], the goal's words and [end], a bank goal's item name cut so the line fits the chat box. */
    private fun goalLine(start: String, goal: Goal, end: String): String {
        if (goal !is Goal.Bank) return "$start${GoalWords.short(goal, names)}$end"
        val head = "${StepAmount.short(goal.amount)} "
        val tail = " in the bank"
        return start + head + chat.fit(item(goal.item), CHAT_ROOM - chat.width(start + head + tail + end)) + tail + end
    }

    /** A reflex lit or put out a prayer (S13 part 3): "Autopilot: reflex 1 prays Protect from melee." / "... stops praying ...". */
    fun prayed(change: PrayerChange): String =
        "${PREFIX}reflex ${change.number} ${if (change.on) "prays" else "stops praying"} ${Prayers.named(change.prayer)}."

    /** Reflex [number] lit the special attack bar (S13 part 4): "Autopilot: reflex 3 used the special attack." */
    fun usedSpecial(number: Int): String = "${PREFIX}reflex $number used the special attack."

    /** The reflex at [number] [fired] and the flow goes on from step [toStep], the player clear of what attacked them. */
    fun jump(number: Int, fired: Fired, toStep: Int): String = "${PREFIX}reflex $number fired ${fired(fired)}, jumping to step $toStep."

    /**
     * Bank step [number] leaves out the withdrawal of [amount] (null for all) of [withdrawn], which fed only step [fed], whose
     * goal the run met (S12, words since S13): "Autopilot: step 3 leaves out 5 logs, step 1 met its goal."
     */
    fun leftOut(number: Int, amount: Int?, withdrawn: Int, fed: Int): String {
        val start = "${PREFIX}step $number leaves out ${amount?.let { "${StepAmount.short(it)} " }.orEmpty()}"
        val end = ", step $fed met its goal."
        return start + chat.fit(item(withdrawn), CHAT_ROOM - chat.width(start + end)) + end
    }

    /** What a reflex fired at: "at 11/20 hitpoints", "under attack", "at 70% special energy". */
    private fun fired(fired: Fired): String =
        when (fired) {
            is Fired.AtHitpoints -> "at ${fired.health.text()} hitpoints"
            is Fired.AtSkill -> "at ${Skill.getName(fired.skill)} ${fired.level}"
            Fired.UnderAttack -> "under attack"
            is Fired.AtSpecialEnergy -> "at ${fired.percent}% special energy"
        }

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
        return if (tool.countable) "Needs ${Reasons.article(word)} $word$usable" else "Needs $word"
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

    /** "No way through the door at 3101, 3509", the tile as the walk step's slot shows one. */
    private fun impassable(passage: Passage, tile: Tile): String =
        "No way ${way(passage.way)} the ${noun(passage)} at ${tile.text().replace(" ", ", ")}"

    private fun noun(passage: Passage): String =
        when (passage) {
            Passage.DOOR -> "door"
            Passage.GATE -> "gate"
            Passage.CURTAIN -> "curtain"
            Passage.LADDER_UP, Passage.LADDER_DOWN -> "ladder"
            Passage.STAIRS_UP, Passage.STAIRS_DOWN -> "stairs"
            Passage.TRAPDOOR -> "trapdoor"
        }

    private fun way(way: Way): String =
        when (way) {
            Way.THROUGH -> "through"
            Way.UP -> "up"
            Way.DOWN -> "down"
        }

    private fun noWay(place: Place): String =
        when (place) {
            Place.TILE -> "No way there from here"
            Place.WORK_SPOT -> "No way to the work spot from here"
        }

    private fun item(id: Int): String = names.item(id).lowercase()

    companion object {
        /** The chat box draws from x 4 and clips at 463 px (`client.java`), so a line has this much room. */
        const val CHAT_ROOM = 459

        /** How many of the slot's lines a block may take (Maxime, 2026-10-11): the rest keep the target and a detail. */
        const val SLOT_LINES = 2

        /** What every chat line of the autopilot starts with, the builder's own lines included. */
        const val PREFIX = "Autopilot: "

        const val DIED = "${PREFIX}stopped, you died."

        /** A whole lap went by with no step working (Maxime, 2026-10-11). */
        const val IDLE_LAP = "${PREFIX}stopped, no step had anything to do."

        /** The flow stops once every goal is met, and every step with one has (S12). */
        const val ALL_GOALS_MET = "${PREFIX}stopped, every goal is met."

        /** Run refused: the flow stops when all goals are met, and no step has one (Maxime, 2026-10-15). */
        const val NO_GOALS = "${PREFIX}no step has an Until goal."

        const val CANNOT_RESUME = "${PREFIX}could not resume your flow. Check it in the flow builder."

        /**
         * The chat line for step [number] when it cannot work, at Run or while running: the reason is on the step's
         * slot, and in full it is often too long for a chat line (Maxime, 2026-10-09).
         */
        fun cannotWork(number: Int): String = "${PREFIX}step $number cannot work yet. See its slot warning in the builder."

        /** The chat line for reflex [number] when it cannot work: its row in the builder shows why. */
        fun reflexCannotWork(number: Int): String = "${PREFIX}reflex $number cannot work yet. See its row in the builder."
    }
}
