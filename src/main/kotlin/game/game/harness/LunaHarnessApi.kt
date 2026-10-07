package game.harness

import api.bot.zone.Zone
import com.google.gson.JsonElement
import game.idle.content.audit.ContentAudit
import game.idle.content.audit.ContentAuditReport
import game.idle.content.audit.ContentRegistries
import game.idle.content.audit.LunaContentFacts
import game.idle.content.audit.LunaRegistries
import game.idle.content.audit.zoneOfRegion
import game.idle.flow.FlowCheck
import game.idle.flow.FlowError
import game.idle.flow.StepSettings
import game.idle.idleState
import game.idle.movement.navigateToReach
import io.luna.game.action.Action
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.GameMessage
import java.lang.management.ManagementFactory
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.CompletableFuture

/**
 * [HarnessApi] over the live world. Reads and actions run on the game thread; only login waits on the HTTP thread,
 * because loading a save must not block a tick.
 */
class LunaHarnessApi(
    private val world: World,
    private val gameThread: GameThread,
    private val headless: HeadlessPlayers,
    private val flows: FlowCheck,
    private val contentRegistries: ContentRegistries = LunaRegistries,
    private val clock: Clock = Clock.systemDefaultZone(),
) : HarnessApi {

    override fun world(): WorldView = gameThread.run {
        WorldView(
            tick = world.currentTick,
            playersOnline = world.players.size(),
            botsOnline = world.bots.onlineCount,
            harnessPlayersOnline = world.players.count { it.isHeadless },
            uptimeSeconds = ManagementFactory.getRuntimeMXBean().uptime / 1000,
        )
    }

    override fun players(includeBots: Boolean): List<PlayerSummary> = gameThread.run {
        world.players.filter { includeBots || !it.isBot }.map(::summary).sortedBy { it.name }
    }

    override fun login(name: String): PlayerView {
        val player = gameThread.await(gameThread.run { headless.login(name) })
        return gameThread.run { playerView(player) }
    }

    override fun logout(name: String): ActionView = gameThread.run {
        val player = online(name)
        headless.logout(player)
        ActionView(player.username, "logout", "forced logout requested; the save finishes asynchronously")
    }

    override fun player(name: String): PlayerView = gameThread.run { playerView(online(name)) }

    override fun nearby(name: String, radius: Int, includeInert: Boolean): NearbyView {
        if (radius !in 1..MAX_RADIUS) {
            throw HarnessException(400, "radius must be between 1 and $MAX_RADIUS, got $radius")
        }
        return gameThread.run { nearbyView(online(name), radius, includeInert) }
    }

    override fun messages(name: String, since: Long, type: String?): MessagesView = gameThread.run {
        val player = online(name)
        val log = messageLogOf(player.client)
            ?: throw HarnessException(501, "${player.username} records no messages; bots never do")
        log.since(since, type)
    }

    override fun act(name: String, action: PlayerAction): ActionView = gameThread.run {
        val player = online(name)
        if (player.isBot) {
            throw HarnessException(409, "${player.username} is a bot and is driven by its own scripts")
        }
        perform(player, action)
    }

    /** Only reading the world needs the game thread; the rules and the text run on the HTTP thread. */
    override fun contentAudit(): ContentAuditView {
        val facts = gameThread.run { LunaContentFacts(world, contentRegistries).collect() }
        val areas = ContentAudit(facts, zoneOfRegion(Zone.entries)).areas()
        return ContentAuditView(ContentAuditReport(areas, LocalDate.now(clock)).files())
    }

    override fun flow(name: String): FlowView = gameThread.run { flowView(online(name)) }

    /** The autopilot owns a running flow's state, so a running flow is never replaced under it. */
    override fun replaceFlow(name: String, steps: List<StepSettings>): FlowView = gameThread.run {
        val player = online(name)
        if (player.isBot) throw HarnessException(409, "${player.username} is a bot and has no flow")
        if (player.idleState.running) throw HarnessException(409, "${player.username}'s flow is running: click Stop first")
        try {
            flows.check(steps)
        } catch (e: FlowError) {
            throw HarnessException(400, e.message)
        }
        player.idleState = player.idleState.withFlow(steps)
        flowView(player)
    }

    private fun flowView(player: Player): FlowView {
        val state = player.idleState
        val steps = state.steps.map { FlowStepView(it.kind, it.values, flows.resolver.types.summary(it)) }
        return FlowView(player.username, state.running, state.stepIndex, steps)
    }

    private fun perform(player: Player, action: PlayerAction): ActionView =
        when (action) {
            is PlayerAction.ClickObject -> clickObject(player, action)
            is PlayerAction.ClickNpc -> clickNpc(player, action)
            is PlayerAction.ClickItem -> clickItem(player, action)
            is PlayerAction.ClickGroundItem -> clickGroundItem(player, action)
            is PlayerAction.Walk -> {
                player.navigator.navigate(Position(action.x, action.y, player.z), true)
                ActionView(player.username, "walk", "walking to ${action.x}, ${action.y}")
            }
            is PlayerAction.Command -> send(player, "command", HarnessPackets.command(action.text))
            is PlayerAction.Chat -> send(player, "chat", HarnessPackets.chat(action.text))
            is PlayerAction.Button -> send(player, "button", HarnessPackets.button(action.id))
            is PlayerAction.PickTile -> send(player, "pick tile", HarnessPackets.mapPick(action.x, action.y))
            PlayerAction.ContinueDialogue -> send(player, "continue dialogue", HarnessPackets.continueDialogue())
            PlayerAction.CloseInterface -> send(player, "close interface", HarnessPackets.closeInterface())
        }

    private fun clickObject(player: Player, action: PlayerAction.ClickObject): ActionView {
        val target = world.objects.findAll(Position(action.x, action.y, player.z))
            .filter { it.id == action.id && it.isVisibleTo(player) }
            .findFirst().orElse(null)
            ?: throw HarnessException(404, "no object ${action.id} at ${action.x}, ${action.y}")
        val packet = HarnessPackets.objectClick(action.option, action.x, action.y, action.id)
        sendOnArrival(player, navigateToReach(player, target), packet)
        return ActionView(player.username, "click object", arrivalDetail(target.def().name, action.option))
    }

    private fun clickNpc(player: Player, action: PlayerAction.ClickNpc): ActionView {
        // A listed NPC is always ACTIVE: removal takes it off the list in the same step that makes it inactive.
        val npc = world.npcs.get(action.index)
            ?.takeIf { it.isViewableFrom(player) }
            ?: throw HarnessException(404, "no NPC with index ${action.index} in view")
        val packet = HarnessPackets.npcClick(action.option, npc.index)
        sendOnArrival(player, player.navigator.navigate(npc, true, false), packet)
        return ActionView(player.username, "click npc", arrivalDetail(npc.def().name, action.option))
    }

    private fun clickItem(player: Player, action: PlayerAction.ClickItem): ActionView {
        val slots = 0 until player.inventory.capacity()
        if (action.slot !in slots) {
            throw HarnessException(400, "inventory slot must be between ${slots.first} and ${slots.last}")
        }
        val item = player.inventory.get(action.slot)
            ?: throw HarnessException(404, "inventory slot ${action.slot} is empty")
        val expectedId = action.id
        if (expectedId != null && expectedId != item.id) {
            throw HarnessException(409, "inventory slot ${action.slot} holds item ${item.id}, not $expectedId")
        }
        return send(player, "click item", HarnessPackets.itemClick(action.option, action.slot, item.id))
    }

    private fun clickGroundItem(player: Player, action: PlayerAction.ClickGroundItem): ActionView {
        val position = Position(action.x, action.y, player.z)
        val target = world.items.findAll(position)
            .filter { it.id == action.id && it.isVisibleTo(player) }
            .findFirst().orElse(null)
            ?: throw HarnessException(404, "no ground item ${action.id} at ${action.x}, ${action.y}")
        val packet = HarnessPackets.groundItemClick(action.option, action.x, action.y, action.id)
        sendOnArrival(player, player.navigator.navigate(position, true), packet)
        return ActionView(player.username, "click ground item", arrivalDetail(target.def().name, action.option))
    }

    private fun send(player: Player, action: String, packet: GameMessage): ActionView {
        player.client.onMessageReceived(packet)
        return ActionView(player.username, action, "opcode ${packet.opcode} queued for the next tick")
    }

    /**
     * The client walks next to a target before it sends the click, and the server never walks for a click
     * (`InteractionAction` only waits while the walking queue drains). So navigate first, as bots do, and send the
     * click once navigation ends, reached or not, which is what the client does. A superseded navigation is
     * cancelled and drops its click.
     */
    private fun sendOnArrival(player: Player, navigation: CompletableFuture<NavigationResult>, packet: GameMessage) {
        navigation.whenComplete { _, failure ->
            if (failure == null && player.state == EntityState.ACTIVE) {
                player.client.onMessageReceived(packet)
            } else {
                packet.payload.releaseAll()
            }
        }
    }

    private fun arrivalDetail(targetName: String, option: Int) = "walking to $targetName; option $option on arrival"

    private fun online(name: String): Player =
        world.getPlayer(name.trim().lowercase()).orElseThrow { HarnessException(404, "$name is not online") }

    private fun summary(player: Player) =
        PlayerSummary(player.username, player.index, player.isBot, player.isHeadless, position(player.position))

    private fun playerView(player: Player) =
        PlayerView(
            name = player.username,
            index = player.index,
            rights = player.rights.name,
            bot = player.isBot,
            harness = player.isHeadless,
            position = position(player.position),
            runEnergy = player.runEnergy,
            running = player.isRunning,
            walking = !player.walking.isEmpty,
            combatLevel = player.combatLevel,
            skills = player.skills.map { SkillView(it.id, it.name, it.level, it.staticLevel, it.experience) },
            inventory = items(player.inventory) { MenuOptions.forInventoryItem(it) },
            equipment = items(player.equipment) { emptyList() },
            bankItems = player.bank.size(),
            actions = player.actions.getAll(Action::class.java).map { readableName(it.javaClass) },
            overlays = player.overlays.overlayMap.entries.associate { (type, overlay) ->
                type.name to readableName(overlay.javaClass)
            },
            attributes = persistentAttributes(player),
        )

    private fun items(container: ItemContainer, options: (List<String>) -> List<OptionView>): List<ItemView> =
        (0 until container.capacity()).mapNotNull { slot ->
            container.get(slot)?.let { item ->
                val def = item.itemDef
                ItemView(slot, item.id, def.name, item.amount, options(def.inventoryActions))
            }
        }

    /**
     * What the next save would write, keyed without Luna's `@class` suffix. A loaded attribute nobody has read
     * this session is missing here, and Luna's save drops it too (`AttributeMap.save` skips `loadedAttributes`).
     */
    private fun persistentAttributes(player: Player): Map<String, JsonElement> =
        player.attributes.save().entries.associate { (key, value) ->
            key.substringBefore("@") to Attribute.getGsonInstance().toJsonTree(value)
        }

    private fun nearbyView(player: Player, radius: Int, includeInert: Boolean): NearbyView {
        val center = player.position
        val objects = world.locator.findObjects(player, radius) { it.isVisibleTo(player) }
            .map { objectView(it, center) }
            .filter { includeInert || it.options.isNotEmpty() }
            .sortedWith(compareBy({ it.distance }, { it.id }))
        val npcs = world.locator.findNpcs(player, radius) { it.isViewableFrom(player) }
            .map { npcView(it, center) }
            .sortedWith(compareBy({ it.distance }, { it.index }))
        val groundItems = world.locator.findItems(player, radius) { it.isVisibleTo(player) }
            .map { groundItemView(it, center) }
            .sortedWith(compareBy({ it.distance }, { it.id }))
        val players = world.locator.findPlayers(player, radius) { it != player }
            .map { NearbyPlayerView(it.username, it.index, it.isBot, position(it.position), distance(center, it)) }
            .sortedWith(compareBy({ it.distance }, { it.name }))
        return NearbyView(position(center), radius, objects, npcs, groundItems, players)
    }

    private fun objectView(obj: GameObject, center: Position) =
        ObjectView(
            id = obj.id,
            name = obj.def().name,
            position = position(obj.position),
            distance = distance(center, obj),
            type = obj.objectType.name,
            options = MenuOptions.forObject(obj.def().actions),
        )

    private fun npcView(npc: Npc, center: Position) =
        NpcView(
            index = npc.index,
            id = npc.id,
            name = npc.def().name,
            position = position(npc.position),
            distance = distance(center, npc),
            combatLevel = npc.combatLevel,
            options = MenuOptions.forNpc(npc.def().actions),
        )

    private fun groundItemView(item: GroundItem, center: Position) =
        GroundItemView(
            id = item.id,
            name = item.def().name,
            amount = item.amount,
            position = position(item.position),
            distance = distance(center, item),
            options = MenuOptions.forGroundItem(item.def().groundActions),
        )

    private fun distance(center: Position, entity: Entity): Int = center.computeLongestDistance(entity.position)

    private fun position(position: Position) = PositionView(position.x, position.y, position.z)

    private companion object {
        const val MAX_RADIUS = 32
    }
}
