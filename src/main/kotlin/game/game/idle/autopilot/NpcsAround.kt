package game.idle.autopilot

import game.idle.location.Area
import game.idle.movement.Doorways
import game.idle.movement.EntranceStep
import game.idle.movement.Footprint
import game.idle.movement.ReachScan
import game.idle.movement.ReachTerrain
import game.idle.movement.Step
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * The npcs around a step's [area] the step [accepts] (a fishing spot of its method, an npc of its target nobody else
 * fights), as the step looks at them: each with the walk to a tile it is reached from under the [policy] of the
 * click (beside a spot or a melee target, in sight and range for a bow), [Candidate.attacking] when it fights the
 * player. The walk is [LunaClicks.reachSteps]': through the shut ones of [doorways] too, and their entrances. An npc is
 * found again by its index, since it may have moved; the step decides whether what stands in its slot is still what
 * the look saw.
 */
class NpcsAround(
    private val player: Player,
    private val area: Area,
    private val accepts: (Npc) -> Boolean,
    private val policy: () -> InteractionPolicy,
    doorways: Doorways,
) : ReachTerrain<Npc> {

    private val world get() = player.world
    private val anchor: Position = area.anchor.toPosition()
    private val scan = ReachScan(anchor, area.radius)
    private val steps = LunaClicks.reachSteps(world, doorways)

    /** Whether the player stands inside the area. */
    fun atLocation(): Boolean = scan.atLocation(player.position)

    /** Each npc the player can walk up to, with how; none from too far off the area or another floor. */
    fun candidates(): List<Candidate> =
        scan.reachable(player.position, around(), { Footprint(it.position, it.size(), it.size()) }, terrain = this)
            .map { (npc, reach) ->
                Candidate(
                    npc.index, npc.position, reach.distance, reach.usableFromHere, reach.approach,
                    attacking = npc.combat.target == player, crossings = reach.crossings,
                )
            }

    /** Whatever npc holds [candidate]'s index now: the one the look saw, another in its slot, or none. */
    fun find(candidate: Candidate): Npc? = world.npcs.get(candidate.id)

    private fun around(): List<Npc> = world.locator.findNpcs(anchor, area.radius, accepts).toList()

    override fun step(from: Position, direction: Direction): Step? = steps.step(from, direction)

    override fun entrances(from: Position): List<EntranceStep> = steps.entrances(from)

    override fun reachedFrom(tile: Position, target: Npc): Boolean = world.collisionManager.reached(tile, target, policy())
}
