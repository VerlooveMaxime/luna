package game.idle.flow

/** A line of a flow the player could not have meant; the message is shown as is. */
class FlowError(override val message: String) : RuntimeException(message)

/** One line of a flow as typed, before locations and trees are checked against the data. */
sealed interface FlowStep {

    /** Chops one kind of tree until the inventory is full. */
    data class Chop(val tree: String, val locationId: String) : FlowStep

    /** Drops what the chop steps before it gathered. */
    data object Drop : FlowStep

    /** Deposits everything but the player's axes at the bank of the location the flow is at. */
    data object BankDepositAll : FlowStep
}
