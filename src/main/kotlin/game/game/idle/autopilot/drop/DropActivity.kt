package game.idle.autopilot.drop

import game.idle.flow.StepActivity

/** What the drop step needs from the player. [LunaItemDropper] is the in-game one. */
interface ItemDropper {

    fun isBusy(): Boolean

    fun hasItems(): Boolean

    fun dropItems()
}

/** The `drop` step: drops its items, over as soon as the bag holds none of them. */
class DropActivity(private val dropper: ItemDropper) : StepActivity {

    override fun isBusy(): Boolean = dropper.isBusy()

    override fun isDone(): Boolean = !dropper.hasItems()

    override fun act() = dropper.dropItems()
}
