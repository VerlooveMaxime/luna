package engine.spawn

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.movement.wandering.WanderingFrequency

/**
 * An [Npc] implementation that always respawns at some point after death. Default is `30` seconds.
 *
 * @author lare96
 */
class PersistentNpc(id: Int, position: Position,
                    private val respawnAfter: Int? = null,
                    private val wanderingRadius: Int? = null,
                    private val wanderingFrequency: WanderingFrequency? = null) : Npc(ctx, id, position) {

    override fun onSpawn() {
        if (wanderingRadius != null && wanderingFrequency != null) {
            startWandering(wanderingRadius, wanderingFrequency);
        } else if (def().name.equals("Imp")) {
            startWandering(1500, WanderingFrequency.NORMAL);
        }
        if (respawnAfter != null) {
            respawnTicks = respawnAfter
        } else if (respawnTicks <= 0) {
            respawnTicks = DEFAULT_RESPAWN_TICKS
        }
    }

    override fun createRespawn(): Npc {
        val respawn = PersistentNpc(baseId, basePosition, respawnAfter, wanderingRadius, wanderingFrequency)
        respawn.respawnTicks = respawnTicks
        respawn.defaultDirection = defaultDirection
        return respawn
    }

    companion object {

        /**
         * The respawn delay of a spawn whose file entry and combat definition give none.
         */
        const val DEFAULT_RESPAWN_TICKS = 50
    }
}