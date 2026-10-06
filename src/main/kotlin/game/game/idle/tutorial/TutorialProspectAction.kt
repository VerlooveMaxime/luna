package game.idle.tutorial

import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player

/** The island's prospecting: the player looks the rock over for a few ticks, locked as Luna's own is, then [prospected]. */
class TutorialProspectAction(player: Player, private val prospected: () -> Unit) : LockedAction(player) {

    override fun run(): Boolean {
        if (executions < PROSPECT_TICKS) {
            return false
        }
        prospected()
        return true
    }

    private companion object {
        /** LostCity's island waits three ticks before naming the ore. */
        const val PROSPECT_TICKS = 3
    }
}
