package game.idle.autopilot.drop

import game.idle.flow.Meter
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork

/** What the drop step needs from the player. [LunaItemDropper] is the in-game one. */
interface ItemDropper {

    fun isBusy(): Boolean

    fun hasItems(): Boolean

    fun dropItems()

    /** Breaks off the action in progress. */
    fun interrupt()
}

/** The `drop` step's work: drops its items, over once the bag holds none of them. */
class DropWork(private val dropper: ItemDropper) : StepWork {

    override val meter: Meter = Meter.None

    override fun isBusy(): Boolean = dropper.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = dropper.interrupt()

    override fun act(left: Int?): StepOutcome = if (dropper.hasItems()) drop() else StepOutcome.Finished

    private fun drop(): StepOutcome {
        dropper.dropItems()
        return StepOutcome.Worked
    }
}
