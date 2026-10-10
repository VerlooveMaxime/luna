package game.idle.flow

import game.idle.location.Area
import game.idle.location.Tile

/**
 * A step with its own work spot that starts outside its area (S07e): [walk] takes the player to the spot's tile as a walk
 * step would, blocking as it does when the tile cannot be reached, then the step's [work] begins. The work is built only
 * once the player arrived, so it starts where it works.
 */
class SpotWalk(private val walk: StepActivity, private val work: () -> StepActivity) : StepActivity, CountsAmount {

    private var working: StepActivity? = null

    override fun isBusy(): Boolean = (working ?: walk).isBusy()

    override fun isDone(): Boolean = working?.isDone() ?: false

    override fun blocked(): String? = (working ?: walk).blocked()

    override fun stopReason(): String? = working?.stopReason()

    override fun amountDone(): Int = (working as? CountsAmount)?.amountDone() ?: 0

    override fun act() {
        val started = working
        if (started != null) return started.act()
        walk.act()
        if (walk.isDone()) working = work().also { it.act() }
    }

    companion object {

        /**
         * The activity of a step whose own spot's area is [area] (null when it works where the flow went), the player
         * standing on [here]: inside the area, or with no spot of its own, the [work] at once (Maxime, 2026-10-11); from
         * outside, the [walk] to the spot's tile first.
         */
        fun of(here: Tile, area: Area?, walk: (Tile) -> StepActivity, work: () -> StepActivity): StepActivity =
            if (area == null || here in area) work() else SpotWalk(walk(area.anchor), work)
    }
}
