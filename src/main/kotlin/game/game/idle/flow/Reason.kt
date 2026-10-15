package game.idle.flow

/** What a step looks for in the world around its work spot. */
enum class Target { TREE, ROCK, FISHING_SPOT, FIRE_OR_RANGE, FURNACE, ANVIL, NPC, BANK_BOOTH, FREE_TILE }

/** Where a walk goes: a tile picked for a walk step, or a step's work spot. */
enum class Place { TILE, WORK_SPOT }

/**
 * Why a step cannot go on, or ended, or was skipped, typed so that every kind reports the same situation the same way
 * and one place ([StepWords]) puts it into words.
 */
sealed interface Reason {

    data class NeedsTool(val tool: ToolNeed) : Reason

    data class NeedsLevel(val skill: Int, val level: Int) : Reason

    data class NoneInReach(val target: Target) : Reason

    data object CannotMake : Reason

    data object WrongWindow : Reason

    data class OtherFloor(val place: Place) : Reason

    data class NoWay(val place: Place) : Reason

    /**
     * A stop or a reflex's jump held while something attacks the player, who runs until nothing does (S13): before the
     * flow stops, or, with [toStep], before it goes on from that step (its number, from 1).
     */
    data class RunningClear(val toStep: Int? = null) : Reason

    /**
     * What the bag or the bank holds stands in the way: what a bank step with something to do is stuck on, what ends a
     * step that fills the bag ([BagFull]); the only reasons a step ends or skips its turn on.
     */
    sealed interface Stuck : Reason

    /** The bag is full; [left] are the item ids a pick-up step left lying because of it, none for a kind that fills it itself. */
    data class BagFull(val left: List<Int>) : Stuck

    data class NoneLeftInBank(val item: Int) : Stuck

    data object BankFull : Stuck
}
