package game.skill.firemaking

import api.predef.*
import io.luna.game.model.mob.interact.InteractionPolicy

for (log in Log.entries) {
    // Use tinderbox on log in inventory.
    useItem(Firemaking.TINDERBOX).onItem(log.id) {
        plr.submitAction(LightLogAction(plr, log, groundLog = null))
    }

    // Use tinderbox on log on floor: the player steps onto the logs and lights them there.
    useItem(Firemaking.TINDERBOX).onGroundItem(log.id, InteractionPolicy.EQUAL_POSITION_BIF) {
        plr.submitAction(LightLogAction(plr, log, groundItem))
    }

    // Use "Light" option with log on ground.
    groundItem2(log.id) {
        plr.submitAction(LightLogAction(plr, log, groundItem))
    }
}
