package game.idle.autopilot.reflex

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.specialAttack.SpecialAttackHandler
import engine.combat.prayer.CombatPrayer
import engine.combat.status.StatusEffectType
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.LunaTeleports
import game.idle.autopilot.travel.Flight
import game.idle.autopilot.travel.FlightTerrain
import game.idle.autopilot.travel.LunaTraveller
import game.idle.autopilot.travel.OpenLeaf
import game.idle.flow.Health
import game.idle.flow.Passage
import game.idle.flow.ReflexBody
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.UnderWay
import game.idle.flow.WornSpecial
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.idle.movement.EntranceStep
import game.idle.movement.FleePlanner
import game.idle.movement.Step
import game.idle.movement.TravelWebs
import game.idle.movement.reachingTiles
import game.obj.doors.DoorType
import game.obj.doors.Doors
import game.player.item.consume.food.Food
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/** The player's run toggle as it was before a flight turned running on, while the flight goes on; never saved. */
private var Player.runToggleBeforeFlight by Attr.nullableObj(Boolean::class)

/** [player]'s hitpoints out of their full hitpoints. */
fun healthOf(player: Player): Health = Health(player.health, player.skill(Skill.HITPOINTS).staticLevel)

/**
 * [ReflexBody] for a logged-in player. Eating and drinking use the same event as the client's click, so Luna's
 * consumables run unchanged (their separate delays included) and the fight in progress goes on. Praying goes through
 * Luna's prayer set as the tab's buttons do (varps, sounds, head icon, drain), never to its state directly, and the
 * special attack through the bar as its button does (the varp, the drain, the special on the next hit). A teleport goes
 * through [LunaTeleports], as a trip's does. A flight goes through the [doorways] of the travel web and shuts a door by the open leaf Luna's
 * doors put in its place, as its [doorTypes] (by a closed door's id) name it.
 */
class LunaReflexBody(
    private val player: Player,
    private val doorways: () -> Doorways = { TravelWebs.SERVER.doorwaysOf(player.world) },
    private val doorTypes: (Int) -> DoorType? = Doors::typeOf,
) : ReflexBody {

    private val world get() = player.world

    private val flight = Flight(LunaTraveller(player, web = null, fleeing = true))

    override fun health(): Health = healthOf(player)

    override fun level(skill: Int): Int = player.skill(skill).level

    override fun potionSlot(doses: List<Int>): Int? =
        doses.firstNotNullOfOrNull { id ->
            (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id == id }
        }

    override fun drink(slot: Int) = click(slot)

    override fun foodSlot(foods: Set<Int>): Int? {
        val wanted = foods.ifEmpty { Food.ID_TO_FOOD.keys }
        return (0 until player.inventory.capacity()).firstOrNull { player.inventory[it]?.id in wanted }
    }

    override fun eat(slot: Int) = click(slot)

    private fun click(slot: Int) {
        val item = player.inventory[slot] ?: return
        LunaClicks.postClosing(player, ItemFirstClickEvent(player, item.id, slot, LunaClicks.INVENTORY))
    }

    override fun praying(prayer: CombatPrayer): Boolean = player.combat.prayers.isActive(prayer)

    /**
     * Luna's own checks, read before asking: asking for a prayer that cannot be lit would still put out the one of its
     * group that is on. A locked player (a teleport, death) takes no clicks, the tab's included.
     */
    override fun canPray(prayer: CombatPrayer): Boolean {
        val skill = player.skill(Skill.PRAYER)
        return !player.isLocked && skill.staticLevel >= prayer.level && skill.level > 0 && StatusEffectType.DRAGON_SCIMITAR !in player.status
    }

    override fun pray(prayer: CombatPrayer): Boolean {
        player.combat.prayers.activateIfOff(prayer)
        return praying(prayer)
    }

    override fun stopPraying(prayer: CombatPrayer): Boolean = player.combat.prayers.deactivate(prayer, silent = false)

    override fun specialEnergy(): Int = player.combat.specialBar.energy

    /** Only a special Luna's scripts register counts: the bar of any other weapon lights nothing when clicked. */
    override fun special(): WornSpecial? {
        val type = player.combat.weapon.specialAttackType ?: return null
        return SpecialAttackHandler.of(type)?.let { WornSpecial(it.drain, attack = !it.activationOnly) }
    }

    /** Luna's own refusals, read before asking: `toggleOn` does nothing on a lit or locked bar. */
    override fun specialBarFree(): Boolean {
        val bar = player.combat.specialBar
        return !player.isLocked && !bar.isActivated && !bar.isLocked
    }

    override fun fighting(): Boolean = player.combat.target != null

    override fun useSpecial(): Boolean {
        player.combat.specialBar.toggleOn()
        return player.combat.specialBar.isActivated
    }

    /** Luna's refusals, read before asking: a refused action would still say why in the chat box. */
    override fun canTeleport(destination: TeleportDestination): Boolean = LunaTeleports.canTeleport(player, destination)

    override fun teleport(
        destination: TeleportDestination,
        starting: (TeleportStarted) -> Unit,
        notWornAgain: (TeleportDestination.Jewellery) -> Unit,
    ): TeleportStarted? = LunaTeleports.teleport(player, destination, starting, notWornAgain)

    override fun teleporting(): Boolean = player.isLocked

    override fun underAttack(): Boolean = threats().isNotEmpty()

    /**
     * The [Flight] takes one decision's worth, its walks found on the game thread (`LunaTraveller`'s fleeing mode); the
     * reflex runs again at each decision until nothing attacks. Running is turned on once, at the flight's start and only
     * with energy left (Maxime, 2026-10-10: no loop of turning it on); once Luna turns it off, the energy gone, the
     * flight walks.
     */
    override fun flee() {
        val threats = threats()
        if (threats.isEmpty()) return
        if (player.runToggleBeforeFlight == null) {
            player.runToggleBeforeFlight = player.isRunning
            if (player.runEnergy > 0.0) player.isRunning = true
        }
        flight.flee(threats.map { it.position }, terrain())
    }

    override fun flightBusy(): Boolean = flight.isBusy()

    override fun flightUnderWay(): UnderWay? = flight.underWay()

    override fun shutBehind(): Passage? = flight.shut()

    /**
     * The world as the flight sees it now: the web's doorways, the player's steps through them and its entrances, the npcs'
     * own walls.
     */
    private fun terrain(): FlightTerrain {
        val known = doorways()
        val steps = LunaClicks.reachSteps(world, known)
        return object : FlightTerrain {
            override val doorways: Doorways = known

            override fun step(from: Position, direction: Direction): Step? = steps.step(from, direction)

            override fun entrances(from: Position): List<EntranceStep> = steps.entrances(from)

            override fun npcStep(from: Position, direction: Direction): Boolean = npcCanStep(from, direction)

            override fun onto(from: Position, direction: Direction): Boolean = canStep(from, direction)

            override fun openLeaf(door: Doorway): OpenLeaf? = openLeafOf(door)
        }
    }

    /** Luna's doors swap an opened door for its open leaf on the door's tile or one beside it. */
    private fun openLeafOf(door: Doorway): OpenLeaf? {
        val open = doorTypes(door.id)?.open ?: return null
        val around = (-1..1).flatMap { dx -> (-1..1).map { dy -> door.position.translate(dx, dy) } }
        val leaf = around.firstNotNullOfOrNull { LunaClicks.objectOn(player, open, it) } ?: return null
        return OpenLeaf(open, leaf.position, reachingTiles(world.collisionManager, leaf, player.position))
    }

    override fun calm() {
        flight.calm()
        val before = player.runToggleBeforeFlight ?: return
        player.isRunning = before
        player.runToggleBeforeFlight = null
    }

    override fun dead(): Boolean = player.health == 0

    /** Npcs fighting the player that can walk up to them. */
    private fun threats(): List<Npc> =
        world.locator.findNpcs(player.position, FleePlanner.THREAT_RANGE) { npc ->
            npc.isAlive && npc.combat.target == player && FleePlanner.canGetAt(npc.position, player.position, ::npcCanStep, ::canStep)
        }.toList()

    /** A player's step: walls, objects and the floor stop it, never a mob. */
    private fun canStep(from: Position, direction: Direction): Boolean = LunaClicks.canStep(world, from, direction)

    private fun npcCanStep(from: Position, direction: Direction): Boolean =
        world.collisionManager.traversable(from, EntityType.NPC, direction)
}
