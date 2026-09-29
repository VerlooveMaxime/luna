package game.idle.flow

/** A line of a flow the player could not have meant; the message is shown as is. */
class FlowError(message: String) : RuntimeException(message)

/** When a chop step is over. Without one the step runs until the flow is stopped. */
sealed interface Until {

    data object InventoryFull : Until

    /** This many logs of the chopped kinds, counting inventory and bank together. */
    data class Logs(val count: Int) : Until

    data class Level(val level: Int) : Until
}

/** One line of a flow as typed, before locations and trees are checked against the data. */
sealed interface FlowStep {

    data class Chop(val trees: List<String>, val locationId: String, val drop: Boolean, val until: Until?) : FlowStep

    /** Deposits everything but the player's axes at the bank of the location the flow is at. */
    data object BankDepositAll : FlowStep

    data object Loop : FlowStep
}
