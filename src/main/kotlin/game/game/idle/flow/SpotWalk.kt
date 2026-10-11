package game.idle.flow

import game.idle.location.Area
import game.idle.location.Tile

/**
 * A step with its own work spot that starts outside its area (S07e): [walk] takes the player to the spot's tile as a walk
 * step would, blocking as it does when the tile cannot be reached, then the step's [work] begins. The work is built only
 * once the player arrived, so it starts where it works.
 */
class SpotWalk(private val walk: StepActivity, private val work: () -> StepActivity) : StepActivity {

    private var working: StepActivity? = null
    private var ended = false

    override fun isBusy(): Boolean = (working ?: walk).isBusy()

    /** A turn ended while it walks is over without the work ever starting. */
    override fun isDone(): Boolean = working?.isDone() ?: ended

    override fun blocked(): Reason? = (working ?: walk).blocked()

    override fun waiting(): Reason? = working?.waiting()

    override fun stopping(): StepStop? = working?.stopping()

    override fun said(): Said? = working?.said()

    override fun amountDone(): Int = working?.amountDone() ?: 0

    override fun worked(): Boolean = working?.worked() ?: false

    override fun endTurn() {
        (working ?: walk).endTurn()
        ended = true
    }

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
