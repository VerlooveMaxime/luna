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

// First click fishing spots.
on(NpcFirstClickEvent::class, REACH)
    .match(233, 234, 235, 236)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcFirstClickEvent::class, REACH)
    .match(309, 310, 311, 314, 315, 317, 318)
    .then { fish(this, Tool.FLY_FISHING_ROD) }

on(NpcFirstClickEvent::class, REACH)
    .match(312, 321)
    .then { fish(this, Tool.LOBSTER_POT) }

npc1(313) {
    fish(this, Tool.BIG_NET)
}

on(NpcFirstClickEvent::class, REACH)
    .match(316, 319, 320, 327, 330, 952) // 952: Tutorial Island
    .then { fish(this, Tool.SMALL_NET) }

on(NpcFirstClickEvent::class, REACH)
    .match(1174, 322)
    .then { fish(this, Tool.MONKFISH_NET) }

// Second click fishing spots.
on(NpcSecondClickEvent::class, REACH)
    .match(309, 316, 319, 310, 311, 314, 315, 317, 318, 320)
    .then { fish(this, Tool.FISHING_ROD) }

on(NpcSecondClickEvent::class, REACH)
    .match(312, 321, 322)
    .then { fish(this, Tool.HARPOON) }

npc2(313) {
    fish(this, Tool.SHARK_HARPOON)
}
