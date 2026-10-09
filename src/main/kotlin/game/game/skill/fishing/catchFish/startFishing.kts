package game.skill.fishing.catchFish

import api.predef.*
import io.luna.game.event.impl.NpcClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Submits a [CatchFishAction] to start fishing.
 */
fun fish(msg: NpcClickEvent, tool: Tool) {
    msg.plr.submitAction(CatchFishAction(msg, tool))
}

// Spots are fished from beside them, as npc1 and npc2 do; without a reach policy a click started fishing from
// wherever the player stood.
val REACH = InteractionPolicy.STANDARD_SIZE_BIF

FishingSpot.entries.forEach { spot ->
    on(NpcFirstClickEvent::class, REACH)
        .match(spot.ids)
        .then { fish(this, spot.firstClick) }

    spot.secondClick?.let { tool ->
        on(NpcSecondClickEvent::class, REACH)
            .match(spot.ids)
            .then { fish(this, tool) }
    }
}
