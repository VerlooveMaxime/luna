package game.idle.autopilot.reflex

import api.bot.webwalk.model.EdgeType
import api.combat.specialAttack.SpecialAttackHandler
import engine.combat.prayer.CombatPrayer.PROTECT_FROM_MELEE
import engine.combat.prayer.CombatPrayer.ROCK_SKIN
import engine.combat.prayer.CombatPrayer.THICK_SKIN
import engine.combat.status.StatusEffectType
import engine.controllers.WildernessController
import engine.controllers.WildernessLocatableController.wildernessLevel
import game.idle.autopilot.EndlessAction
import game.idle.flow.Health
import game.idle.flow.Passage
import game.idle.flow.TeleportDestination
import game.idle.flow.UnderWay
import game.idle.flow.WornSpecial
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.item.degradable.jewellery.TeleportJewellery
import game.obj.doors.DoorType
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay
import game.skill.magic.teleportSpells.TeleportSpell
import game.testworld.TestWorld
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerRights
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.combat.SpecialAttackType
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

class LunaReflexBodyTest {

    private val here = Position(3200, 3200)
    private val chicken = 41
    private val bread = 2309
    private val trout = 333
    private val logs = 1511
    private val deadTree = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("reflexes", here)

    private fun attackedBy(player: Player, at: Position): Npc = TestWorld.spawnNpc(chicken, at).also { it.combat.target = player }

    private fun eats(): MutableList<Pair<Int, Int>> {
        val eaten = mutableListOf<Pair<Int, Int>>()
        TestWorld.listen(ItemFirstClickEvent::class.java) { eaten += it.id to it.index }
        return eaten
    }

    @Test
    fun `health is the player's hitpoints out of their full hitpoints`() {
        val player = login()
        player.health = 6

        assertEquals(Health(6, 10), LunaReflexBody(player).health())
    }

    @Test
    fun `with no food picked the first food in the bag is found`() {
        val player = login()
        player.inventory.set(0, Item(logs))
        player.inventory.set(2, Item(bread))

        assertEquals(2, LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `with foods picked only those are found, first in bag order`() {
        val player = login()
        player.inventory.set(1, Item(bread))
        player.inventory.set(3, Item(trout))

        assertEquals(3, LunaReflexBody(player).foodSlot(setOf(trout)))
    }

    @Test
    fun `without food there is no food slot`() {
        val player = login()
        player.inventory.add(Item(logs))

        assertNull(LunaReflexBody(player).foodSlot(emptySet()))
    }

    @Test
    fun `eating clicks the food like the client`() {
        val player = login()
        player.inventory.set(4, Item(bread))
        val eaten = eats()

        LunaReflexBody(player).eat(4)

        assertEquals(listOf(bread to 4), eaten)
    }

    @Test
    fun `an empty slot is not eaten`() {
        val eaten = eats()

        LunaReflexBody(login()).eat(4)

        assertEquals(emptyList<Pair<Int, Int>>(), eaten)
    }

    @Test
    fun `an npc fighting the player with an open way attacks them`() {
        val player = login()
        attackedBy(player, Position(3203, 3200))

        assertTrue(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting the player but fenced in does not attack them`() {
        val player = login()
        val npc = attackedBy(player, Position(3205, 3200))
        Direction.ALL_EXCEPT_NONE.forEach { TestWorld.place(deadTree, npc.position.translate(1, it)) }

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `an npc fighting someone else does not attack the player`() {
        val player = login()
        attackedBy(TestWorld.login("other", Position(3204, 3200)), Position(3203, 3200))

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `a dead npc does not attack`() {
        val player = login()
        attackedBy(player, Position(3203, 3200)).health = 0

        assertFalse(LunaReflexBody(player).underAttack())
    }

    @Test
    fun `with nothing attacking the player stays put and keeps their run toggle`() {
        val player = login()

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertEquals(listOf(true, false), listOf(player.walking.isEmpty, player.isRunning))
    }

    @Test
    fun `fleeing runs from the attacker`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `fleeing runs, the run toggle on`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()

        assertTrue(player.isRunning)
    }

    @Test
    fun `with no run energy the flight walks, the run toggle left alone`() {
        val player = login().apply { setRunEnergy(0.0, true) }
        attackedBy(player, Position(3199, 3200))

        LunaReflexBody(player).flee()

        assertFalse(player.isRunning)
    }

    @Test
    fun `running is turned on once a flight, so a flight out of energy walks on`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        LunaReflexBody(player).flee()
        player.isRunning = false

        LunaReflexBody(player).flee()

        assertFalse(player.isRunning)
    }

    @Test
    fun `once calm the run toggle is back as it was before the flight`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        LunaReflexBody(player).flee()
        LunaReflexBody(player).flee()

        LunaReflexBody(player).calm()

        assertFalse(player.isRunning)
    }

    @Test
    fun `a flight on the run holds no decision back`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        val body = LunaReflexBody(player)

        body.flee()

        assertFalse(body.flightBusy())
    }

    private val door = 1530
    private val openDoor = 1531

    /** A room of dead trees east of the player, inside x 3203 to 3205 and y 3199 to 3201, its one way in a door here. */
    private val doorTile = Position(3202, 3200)
    private val roomDoorways = Doorways(mapOf((Position(3201, 3200) to doorTile) to Doorway(door, doorTile, EdgeType.DOOR)))

    private fun buildRoom() =
        (3202..3206).flatMap { x -> (3198..3202).map { y -> Position(x, y) } }
            .filter { (it.x == 3202 || it.x == 3206 || it.y == 3198 || it.y == 3202) && it != doorTile }
            .forEach { TestWorld.place(deadTree, it) }

    private fun shutDoor(): GameObject = TestWorld.place(door, doorTile, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

    /** The door standing open: Luna's doors swap it for this leaf. */
    private fun openLeaf(): GameObject = TestWorld.place(openDoor, doorTile, ObjectType.STRAIGHT_WALL, ObjectDirection.NORTH)

    private val doorType = DoorType(door, openDoor, side = null, openSound = null, closeSound = null, duration = null)

    private fun roomBody(player: Player) = LunaReflexBody(player, doorways = { roomDoorways }, doorTypes = { if (it == door) doorType else null })

    private fun doorClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @Test
    fun `a flight to the room that shelters the player opens its door on the way`() {
        val player = login()
        attackedBy(player, Position(3197, 3200))
        buildRoom()
        shutDoor()
        val clicked = doorClicks()
        val body = roomBody(player)

        body.flee()
        TestWorld.tick(5)

        assertEquals(listOf(door) to UnderWay.Opening(Passage.DOOR), clicked to body.flightUnderWay())
    }

    @Test
    fun `a flight holds the next decision while its click on a door lands`() {
        val player = login()
        attackedBy(player, Position(3197, 3200))
        buildRoom()
        shutDoor()
        val body = roomBody(player)

        body.flee()

        assertTrue(body.flightBusy())
    }

    /** The player went through the open door of the room and stands just inside, the attacker still outside. */
    private fun inside(body: (Player) -> LunaReflexBody = { roomBody(it) }, leaf: Boolean = true): Pair<Player, LunaReflexBody> {
        val player = TestWorld.login("reflexes", Position(3201, 3200))
        attackedBy(player, Position(3197, 3200))
        buildRoom()
        if (leaf) openLeaf()
        val flying = body(player)
        flying.flee()
        player.move(doorTile)
        return player to flying
    }

    @Test
    fun `through the door with the attacker beyond it, the flight shuts it`() {
        val clicked = doorClicks()
        val (_, body) = inside()

        body.flee()
        TestWorld.tick(3)

        assertEquals(listOf(openDoor) to UnderWay.Closing(Passage.DOOR), clicked to body.flightUnderWay())
    }

    @Test
    fun `a door the flight shut is told once the flight is over`() {
        val (_, body) = inside()
        val leaf = TestWorld.world.objects.findAll(doorTile).filter { it.id == openDoor }.findFirst().orElseThrow()
        body.flee()
        TestWorld.tick(3)
        TestWorld.world.objects.unregister(leaf)
        shutDoor()

        body.calm()

        assertEquals(Passage.DOOR, body.shutBehind())
    }

    @Test
    fun `a door Luna's doors do not know is not shut`() {
        val (_, body) = inside(body = { LunaReflexBody(it, doorways = { roomDoorways }) })

        body.flee()

        assertFalse(body.flightUnderWay() is UnderWay.Closing)
    }

    @Test
    fun `a door whose open leaf is nowhere beside it is not shut`() {
        val (_, body) = inside(leaf = false)

        body.flee()

        assertFalse(body.flightUnderWay() is UnderWay.Closing)
    }

    @Test
    fun `calm without a flight leaves the run toggle alone`() {
        val player = login().apply { isRunning = true }

        LunaReflexBody(player).calm()

        assertTrue(player.isRunning)
    }

    /** A player with base Prayer [level] and as many points. */
    private fun praying(level: Int): Player = login().also { player ->
        val prayer = player.skill(Skill.PRAYER)
        prayer.staticLevel = level
        prayer.level = level
    }

    @Test
    fun `a prayer is on once Luna's set has it`() {
        val player = praying(1)
        val body = LunaReflexBody(player)
        val before = body.praying(THICK_SKIN)

        player.combat.prayers.activate(THICK_SKIN)

        assertEquals(listOf(false, true), listOf(before, body.praying(THICK_SKIN)))
    }

    @Test
    fun `praying lights the prayer through Luna's set and says it is on`() {
        val player = praying(1)

        assertTrue(LunaReflexBody(player).pray(THICK_SKIN))
        assertTrue(player.combat.prayers.isActive(THICK_SKIN))
    }

    @Test
    fun `praying a prayer already on leaves it on`() {
        val player = praying(1)
        player.combat.prayers.activate(THICK_SKIN)

        assertTrue(LunaReflexBody(player).pray(THICK_SKIN))
        assertTrue(player.combat.prayers.isActive(THICK_SKIN))
    }

    @Test
    fun `a prayer Luna turns down is not on afterwards`() {
        val player = praying(1)

        assertFalse(LunaReflexBody(player).pray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `a prayer can be lit with the base level, points and no disabling`() {
        assertTrue(LunaReflexBody(praying(43)).canPray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `a prayer above the base level cannot be lit, however boosted`() {
        val player = praying(42)
        player.skill(Skill.PRAYER).level = 60

        assertFalse(LunaReflexBody(player).canPray(PROTECT_FROM_MELEE))
    }

    @Test
    fun `without prayer points no prayer can be lit`() {
        val player = praying(43)
        player.skill(Skill.PRAYER).level = 0

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `a locked player, teleporting or dying, lights no prayer`() {
        val player = praying(43)
        player.lock()

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `while a dragon scimitar's hit disables prayers none can be lit`() {
        val player = praying(43)
        player.status.add(checkNotNull(StatusEffectType.DRAGON_SCIMITAR.loadFunction(player)))

        assertFalse(LunaReflexBody(player).canPray(THICK_SKIN))
    }

    @Test
    fun `putting out a prayer that is on says it was`() {
        val player = praying(10)
        player.combat.prayers.activate(ROCK_SKIN)

        assertTrue(LunaReflexBody(player).stopPraying(ROCK_SKIN))
        assertFalse(player.combat.prayers.isActive(ROCK_SKIN))
    }

    @Test
    fun `putting out a prayer that is off says it was not`() {
        assertFalse(LunaReflexBody(praying(10)).stopPraying(ROCK_SKIN))
    }

    @Test
    fun `fleeing stops what the player was doing`() {
        val player = login()
        attackedBy(player, Position(3199, 3200))
        player.submitAction(EndlessAction(player))

        LunaReflexBody(player).flee()
        TestWorld.tick()

        assertNull(player.actions.first(EndlessAction::class.java))
    }

    @Test
    fun `a player with hitpoints left is alive`() {
        assertFalse(LunaReflexBody(login()).dead())
    }

    @Test
    fun `a player with no hitpoints left is dead`() {
        val player = login()
        player.health = 0

        assertTrue(LunaReflexBody(player).dead())
    }

    @Test
    fun `skill levels include a boost and depleted prayer points`() {
        val player = login()
        player.skill(2).level = 4
        player.skill(5).level = 0
        val body = LunaReflexBody(player)

        assertEquals(listOf(4, 0), listOf(body.level(2), body.level(5)))
    }

    @Test
    fun `potions follow list order before bag order`() {
        val player = login()
        player.inventory.set(0, Item(113))
        player.inventory.set(3, Item(2440))

        assertEquals(3, LunaReflexBody(player).potionSlot(listOf(161, 159, 157, 2440, 119, 117, 115, 113)))
    }

    @Test
    fun `the lowest held dose is drunk before a full vial`() {
        val player = login()
        player.inventory.set(0, Item(113))
        player.inventory.set(3, Item(117))
        player.inventory.set(5, Item(119))

        assertEquals(5, LunaReflexBody(player).potionSlot(listOf(119, 117, 115, 113)))
    }

    @Test
    fun `an absent first potion falls through to the next kind`() {
        val player = login()
        player.inventory.set(2, Item(113))

        assertEquals(2, LunaReflexBody(player).potionSlot(listOf(161, 159, 157, 2440, 119, 117, 115, 113)))
    }

    @Test
    fun `no matching potion means drinking cannot be done`() {
        val player = login()
        player.inventory.set(0, Item(229))

        assertNull(LunaReflexBody(player).potionSlot(listOf(119, 117, 115, 113)))
    }

    @Test
    fun `drinking clicks the potion like the client`() {
        val player = login()
        player.inventory.set(4, Item(113))
        val clicked = eats()

        LunaReflexBody(player).drink(4)

        assertEquals(listOf(113 to 4), clicked)
    }

    @Test
    fun `a potion slot emptied before drinking is ignored`() {
        val clicked = eats()

        LunaReflexBody(login()).drink(4)

        assertEquals(emptyList<Pair<Int, Int>>(), clicked)
    }

    private val darklight = 6746
    private val excalibur = 35

    /** The dragon halberd: no test registers its special, as `TestWorld` loads no plugin. */
    private val unregisteredHalberd = 3204

    /** A player wielding [weapon], the combat weapon refreshed as the equipment plugin does on a real server. */
    private fun wielding(weapon: Int): Player = login().also { player ->
        player.equipment.set(Equipment.WEAPON, Item(weapon))
        player.combat.weapon.refreshWeapon(player.combat.weapon.styleDef.stance)
    }

    /** Darklight's and Excalibur's specials as their scripts register them, since `TestWorld` loads no plugin. */
    private fun registerSpecials() {
        SpecialAttackHandler.attack(SpecialAttackType.DARKLIGHT, drain = 50) {}
        SpecialAttackHandler.activation(SpecialAttackType.EXCALIBUR, drain = 100) {}
    }

    @Test
    fun `special energy is the bar's`() {
        val player = login()
        player.combat.specialBar.setEnergy(40)

        assertEquals(40, LunaReflexBody(player).specialEnergy())
    }

    @Test
    fun `without a weapon there is no special`() {
        assertNull(LunaReflexBody(login()).special())
    }

    @Test
    fun `a weapon whose special is not registered has none`() {
        assertNull(LunaReflexBody(wielding(unregisteredHalberd)).special())
    }

    @Test
    fun `a worn weapon's registered special says its drain and that it lands with a hit`() {
        registerSpecials()

        assertEquals(WornSpecial(50, attack = true), LunaReflexBody(wielding(darklight)).special())
    }

    @Test
    fun `a boost special fires by itself`() {
        registerSpecials()

        assertEquals(WornSpecial(100, attack = false), LunaReflexBody(wielding(excalibur)).special())
    }

    @Test
    fun `a locked player, teleporting or dying, keeps the weapon's special as a fact`() {
        registerSpecials()
        val player = wielding(darklight)
        player.lock()

        assertEquals(WornSpecial(50, attack = true), LunaReflexBody(player).special())
    }

    @Test
    fun `the bar is free until Luna lights it`() {
        registerSpecials()
        val player = wielding(darklight)
        val body = LunaReflexBody(player)
        val before = body.specialBarFree()

        player.combat.specialBar.toggleOn()

        assertEquals(listOf(true, false), listOf(before, body.specialBarFree()))
    }

    @Test
    fun `a bar locked by a special under way is not free`() {
        val player = login()
        player.combat.specialBar.isLocked = true

        assertFalse(LunaReflexBody(player).specialBarFree())
    }

    @Test
    fun `a locked player, teleporting or dying, has no free bar`() {
        val player = login()
        player.lock()

        assertFalse(LunaReflexBody(player).specialBarFree())
    }

    @Test
    fun `fighting means a combat target`() {
        val player = login()
        val body = LunaReflexBody(player)
        val before = body.fighting()

        player.combat.target = TestWorld.spawnNpc(chicken, here.translate(1, 0))

        assertEquals(listOf(false, true), listOf(before, body.fighting()))
    }

    @Test
    fun `using the special lights the bar through Luna and says it is lit`() {
        registerSpecials()
        val player = wielding(darklight)

        assertTrue(LunaReflexBody(player).useSpecial())
        assertTrue(player.combat.specialBar.isActivated)
    }

    @Test
    fun `a special Luna turns down is not lit afterwards`() {
        assertFalse(LunaReflexBody(wielding(unregisteredHalberd)).useSpecial())
    }

    @Test
    fun `a locked bar is not lit however it is asked`() {
        registerSpecials()
        val player = wielding(darklight)
        player.combat.specialBar.isLocked = true

        assertFalse(LunaReflexBody(player).useSpecial())
    }

    @Test
    fun `with energy short of the drain Luna leaves the bar off`() {
        registerSpecials()
        val player = wielding(darklight)
        player.combat.specialBar.setEnergy(40)

        assertFalse(LunaReflexBody(player).useSpecial())
    }

    private val varrock = TeleportDestination.Spell(TeleportSpell.VARROCK)
    private val paddewwa = TeleportDestination.Spell(TeleportSpell.PADDEWWA)
    private val edgeville = TeleportDestination.Jewellery(TeleportJewellery.AMULET_OF_GLORY, 0)
    private val airRune = 556
    private val fireRune = 554
    private val lawRune = 563
    private val glory = 1712

    /** A plain player (development mode logs everyone in as a developer, whom Luna lets cast anything) with Magic 25 and the runes for a Varrock teleport. */
    private fun mage(): Player {
        val player = login()
        player.rights = PlayerRights.PLAYER
        val magic = player.skill(Skill.MAGIC)
        magic.staticLevel = 25
        magic.level = 25
        player.inventory.add(Item(airRune, 3))
        player.inventory.add(Item(fireRune))
        player.inventory.add(Item(lawRune))
        return player
    }

    /**
     * Luna's development mode (our `luna.jsonc`) waives levels and runes, so the rules are read with it off, or on
     * whatever the local config says; the settings are the one boundary mocked here.
     */
    private fun <T> withoutDevelopmentMode(block: () -> T): T = inDevelopmentMode(on = false, block)

    private fun <T> inDevelopmentMode(on: Boolean, block: () -> T): T {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.game().betaMode()).thenReturn(on)
        return mockStatic(Luna::class.java, CALLS_REAL_METHODS).use { luna ->
            luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
            block()
        }
    }

    @Test
    fun `a spell can be cast with the level, the runes and its spellbook`() {
        val body = LunaReflexBody(mage())

        assertTrue(withoutDevelopmentMode { body.canTeleport(varrock) })
    }

    @Test
    fun `a spell cannot be cast without its runes`() {
        val player = mage()
        player.inventory.clear()
        val body = LunaReflexBody(player)

        assertFalse(withoutDevelopmentMode { body.canTeleport(varrock) })
    }

    @Test
    fun `a spell cannot be cast below its level`() {
        val player = mage()
        player.skill(Skill.MAGIC).staticLevel = 24
        player.skill(Skill.MAGIC).level = 24
        val body = LunaReflexBody(player)

        assertFalse(withoutDevelopmentMode { body.canTeleport(varrock) })
    }

    @Test
    fun `in development mode Luna waives the level and the runes, and so does the reflex`() {
        val player = login()
        player.rights = PlayerRights.PLAYER
        player.inventory.clear()
        val body = LunaReflexBody(player)

        assertTrue(inDevelopmentMode(on = true) { body.canTeleport(varrock) })
    }

    @Test
    fun `a spell of the other spellbook cannot be cast`() {
        assertFalse(LunaReflexBody(mage()).canTeleport(paddewwa))
    }

    @Test
    fun `a spell of the player's spellbook can, the Ancient one included`() {
        val player = mage()
        player.spellbook = Spellbook.ANCIENT

        assertTrue(LunaReflexBody(player).canTeleport(paddewwa))
    }

    @Test
    fun `a locked player, teleporting or dying, cannot teleport`() {
        val player = mage()
        player.lock()

        assertFalse(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `a tele-blocked player cannot teleport`() {
        val player = mage()
        player.status.add(checkNotNull(StatusEffectType.TELEBLOCK.loadFunction(player)))

        assertFalse(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `deeper in the wilderness than a spell works from, it cannot be cast`() {
        val player = mage()
        player.wildernessLevel = 21

        assertFalse(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `at the deepest level a spell works from it can`() {
        val player = mage()
        player.wildernessLevel = 20

        assertTrue(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `jewellery works from deeper than a spell`() {
        val player = login()
        player.inventory.add(Item(glory))
        player.wildernessLevel = 30

        assertTrue(LunaReflexBody(player).canTeleport(edgeville))
    }

    @Test
    fun `within two ticks of a click the spellbook's throttle holds the next`() {
        val player = mage()
        player.teleportDelay.ready(TeleportAction.CLICK_THROTTLE)
        TestWorld.tick()

        assertFalse(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `two ticks after a click the throttle has passed`() {
        val player = mage()
        player.teleportDelay.ready(TeleportAction.CLICK_THROTTLE)
        TestWorld.tick(2)

        assertTrue(LunaReflexBody(player).canTeleport(varrock))
    }

    @Test
    fun `once landed the spell can be cast again`() {
        val player = mage()
        val body = LunaReflexBody(player)
        body.teleport(varrock)
        TestWorld.tick(6)

        assertTrue(body.canTeleport(varrock))
    }

    @Test
    fun `a cast within the throttle is dropped, as the button drops it`() {
        val player = mage()
        player.teleportDelay.ready(TeleportAction.CLICK_THROTTLE)

        val taken = LunaReflexBody(player).teleport(varrock)

        assertEquals(listOf(false, false), listOf(taken, player.isLocked))
    }

    @Test
    fun `casting a spell locks the player and says Luna took it`() {
        val player = mage()

        val taken = LunaReflexBody(player).teleport(varrock)

        assertEquals(listOf(true, true), listOf(taken, player.isLocked))
    }

    @Test
    fun `a cast spell lands the player at its destination, Luna's line said`() {
        val player = mage()
        LunaReflexBody(player).teleport(varrock)

        TestWorld.tick(6)

        assertEquals(listOf<Any>(TeleportSpell.VARROCK.destination, false), listOf(player.position, player.isLocked))
        assertTrue("You teleport to Varrock." in TestWorld.chatbox(player))
    }

    @Test
    fun `a spell Luna turns down leaves the player where they are, unlocked`() {
        val player = mage()
        player.controllers.register(WildernessController(player))
        player.wildernessLevel = 21

        val taken = LunaReflexBody(player).teleport(varrock)

        assertEquals(listOf<Any>(false, false, here), listOf(taken, player.isLocked, player.position))
    }

    @Test
    fun `the teleport holds the player until they land`() {
        val player = mage()
        val body = LunaReflexBody(player)
        body.teleport(varrock)
        val during = body.teleporting()

        TestWorld.tick(6)

        assertEquals(listOf(true, false), listOf(during, body.teleporting()))
    }

    @Test
    fun `jewellery can be used with a charged piece in the bag, whatever its charge`() {
        val player = login()
        player.inventory.add(Item(1706))

        assertTrue(LunaReflexBody(player).canTeleport(edgeville))
    }

    @Test
    fun `jewellery cannot be used without a charged piece, an uncharged one being none`() {
        val player = login()
        player.inventory.add(Item(1704))

        assertFalse(LunaReflexBody(player).canTeleport(edgeville))
    }

    @Test
    fun `rubbing jewellery lands the player near its destination and uses a charge`() {
        val player = login()
        player.inventory.set(3, Item(glory))
        val body = LunaReflexBody(player)

        val taken = body.teleport(edgeville)
        TestWorld.tick(6)

        assertEquals(listOf<Any?>(true, TeleportJewellery.AMULET_OF_GLORY.destinations[0].centre, 1710), listOf(taken, player.position, player.inventory[3]?.id))
    }

    @Test
    fun `rubbing jewellery says Luna's lines`() {
        val player = login()
        player.inventory.add(Item(glory))
        LunaReflexBody(player).teleport(edgeville)

        TestWorld.tick(6)

        assertEquals(listOf("You rub the amulet...", "Your amulet has three charges left."), TestWorld.chatbox(player).filter { "amulet" in it })
    }

    @Test
    fun `jewellery the bag no longer holds teleports nobody`() {
        val player = login()

        assertFalse(LunaReflexBody(player).teleport(edgeville))
    }
}
