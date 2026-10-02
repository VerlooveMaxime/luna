package game.skill.firemaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType

/**
 * A [LightAction] implementation that enables lighting logs to create fires: logs from the inventory, or
 * [groundLog], logs the player stands on.
 *
 * @author lare96
 */
class LightLogAction(plr: Player, val log: Log, private val groundLog: GroundItem?) : LightAction(plr) {

    // TODO@0.5.0 Implement correct sounds: FLINT1, FIRE_LIT, TINDERBOX_STRIKE(2017)

    /**
     * The position that the log will be placed on.
     */
    private var logGroundItem: GroundItem? = null

    companion object {

        /**
         * Directions in prioritized order to try to walk after lighting a log
         */
        private val WALK_DIRECTIONS: List<Direction> =
            listOf(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH)
    }

    override fun canLight(): Boolean {

        return when {
            blocked() -> {
                mob.sendMessage("You cannot light a fire here.")
                false
            }

            mob.firemaking.level < log.level -> {
                mob.sendMessage("You need a Firemaking level of ${log.level} to light this.")
                false
            }

            else -> {
                if (groundLog == null) {
                    if (mob.inventory.remove(log.id)) {
                        logGroundItem = world.addItem(log.id, 1, mob.position, mob)
                        mob.sendMessage("You attempt to light the logs.")
                        return true
                    }
                    return false
                }
                logGroundItem = groundLog
                mob.sendMessage("You attempt to light the logs.")
                return true
            }
        }
    }

    override fun onLight() {
        if (blocked()) {
            mob.sendMessage("You cannot light a fire here.")
            return
        }
        light()
    }

    /**
     * Attempts to light the fire if the log is still on the ground.
     */
    private fun light() {
        if (logGroundItem != null && world.removeItem(logGroundItem!!)) {
            val firePosition = mob.position
            mob.sendMessage("The fire catches and the logs begin to burn.")
            mob.firemaking.addExperience(log.exp)

            // Walk in a non-blocked direction prioritizing west.
            for (dir in WALK_DIRECTIONS) {
                if (mob.navigator.step(dir)) {
                    mob.actions.submit(object : LockedAction(mob, false, 1) {
                        override fun run(): Boolean {
                            mob.face(dir.opposite())
                            return true
                        }
                    })
                    break
                }
            }
            val fireObject = world.addObject(Firemaking.FIRE_OBJECT, firePosition)
            world.scheduleOnce(rand(Firemaking.BURN_TIME)) {
                world.removeObject(fireObject)
                world.addItem(Firemaking.ASHES, 1, fireObject.position)
            }
        }
    }

    /**
     * Determines if the player cannot light a fire on their current position.
     */
    private fun blocked(): Boolean {
        return world.objects.findAll(mob.position)
            .filter {
                it.objectType == ObjectType.DEFAULT ||
                        it.objectType == ObjectType.STRAIGHT_WALL || it.objectType == ObjectType.DIAGONAL_WALL ||
                        it.objectType == ObjectType.WALL_CORNER || it.objectType == ObjectType.DIAGONAL_CORNER_WALL
            }.count() > 0
    }
}

