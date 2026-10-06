package game.testworld

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import game.harness.MessageLog
import game.harness.RecordedMessage
import game.harness.RecordingGameClient
import game.harness.loggedInChannel
import game.harness.messageLogOf
import game.idle.location.survey.CacheMap
import io.luna.LunaContext
import io.luna.TestContexts
import io.luna.game.GameTicks
import io.luna.game.cache.codec.ItemDefinitionDecoder
import io.luna.game.cache.codec.NpcDefinitionDecoder
import io.luna.game.cache.codec.VarBitDefinitionDecoder
import io.luna.game.cache.codec.VarpDefinitionDecoder
import io.luna.game.cache.codec.WidgetDefinitionDecoder
import io.luna.game.cache.map.MapIndex
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.cache.map.MapObjectSet
import io.luna.game.cache.map.MapTileGridSet
import io.luna.game.event.Event
import io.luna.game.event.EventListener
import io.luna.game.event.EventListenerPipelineSet
import io.luna.game.event.EventPriority
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerCredentials
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.net.client.GameClient
import io.luna.util.parser.impl.EquipmentDefinitionFileParser
import io.luna.util.parser.impl.MessageRepositoryFileParser
import io.luna.util.parser.impl.NpcCombatDefinitionFileParser
import io.luna.util.parser.impl.WeaponDefinitionFileParser
import io.luna.util.parser.impl.WeaponTypeDefinitionFileParser
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.function.Consumer

/**
 * The one real Luna world of the test JVM, built without booting the server: definitions from the cache, an open map
 * with no terrain, no plugins, and the test thread as the game thread. What a test adds, [reset] removes.
 *
 * One per JVM because Kotlin content reaches the context through `api.predef`, which binds it once.
 */
object TestWorld {

    private const val MIN_X = 3072
    private const val MAX_X = 3327
    private const val MIN_Y = 3136
    private const val MAX_Y = 3519
    private const val REGION_SIZE = 64

    private val built: LunaContext by lazy(::build)

    /** Skips the calling test when the cache is absent (it is gitignored), as the survey tests do. */
    val context: LunaContext
        get() {
            assumeTrue(CacheMap.isPresent, "the 377 cache is not in data/game/cache")
            return built
        }

    val world: World get() = context.world

    fun tick(times: Int = 1) = repeat(times) { GameTicks.tick(context.game) }

    /** A player with a fresh save, in the world at [position], whose client records what the server sends. */
    fun login(name: String, position: Position): Player =
        enter(Player(context, PlayerCredentials(name, "password")), position) { player ->
            val log = MessageLog(capacity = 500) { world.currentTick }
            RecordingGameClient(player, context.server.messageRepository, log)
        }

    /** A player whose client writes to a channel as a real client's does; nothing reads it. */
    fun loginWithRealClient(name: String, position: Position): Player =
        enter(Player(context, PlayerCredentials(name, "password")), position) { player ->
            GameClient(loggedInChannel(), context.server.messageRepository, player)
        }

    /** A temporary bot (it never saves) in the world at [position]. Its brain runs on every tick a test drives. */
    fun bot(name: String, position: Position): Bot {
        val bot = Bot.Builder(context).setUsername(name).setTemporary().build()
        bot.loadData(null)
        check(world.players.add(bot)) { "the world refused bot $name" }
        world.bots.add(bot)
        bot.state = EntityState.ACTIVE
        bot.move(position)
        return bot
    }

    private fun enter(player: Player, position: Position, client: (Player) -> GameClient): Player {
        player.setClient(client(player))
        player.loadData(null)
        check(world.players.add(player)) { "the world refused ${player.username}" }
        player.state = EntityState.ACTIVE
        player.move(position)
        return player
    }

    fun messages(player: Player): List<RecordedMessage> = checkNotNull(messageLogOf(player.client)).since(0).messages

    /** The chat box lines [player] was sent, oldest first. */
    fun chatbox(player: Player): List<String> =
        messages(player).filter { it.type == "GameChatboxMessageWriter" }.map { it.fields.getValue("message").toString() }

    /** An object of [id] on [position], which blocks the tile as a map object would if its definition is solid. */
    fun place(
        id: Int,
        position: Position,
        type: ObjectType = ObjectType.DEFAULT,
        direction: ObjectDirection = ObjectDirection.NORTH,
    ): GameObject = register(GameObject.createStatic(context, id, position, type, direction))

    /** An object only [viewer] sees, as content spawns for one player. */
    fun placeFor(viewer: Player, id: Int, position: Position): GameObject =
        register(
            GameObject.createDynamic(
                context, id, position, ObjectType.DEFAULT, ObjectDirection.NORTH, ChunkUpdatableView.localView(viewer),
            ),
        )

    fun spawnNpc(id: Int, position: Position): Npc {
        val npc = Npc(context, id, position)
        check(world.npcs.add(npc)) { "the world refused npc $id" }
        return npc
    }

    /** A ground item everyone sees, or only [viewer] when given, as a player's drop is at first. */
    fun dropItem(id: Int, amount: Int, position: Position, viewer: Player? = null): GroundItem {
        val view = viewer?.let { ChunkUpdatableView.localView(it) } ?: ChunkUpdatableView.globalView()
        val item = GroundItem(context, id, amount, position, view)
        check(world.items.register(item)) { "ground item $id was not registered at $position" }
        return item
    }

    private fun register(obj: GameObject): GameObject {
        check(world.objects.register(obj)) { "object ${obj.id} was not registered at ${obj.position}" }
        return obj
    }

    /** Stands in for a content script's listener; clicks reach it from a tile next to the target. */
    fun <E : Event> listen(type: Class<E>, listener: (E) -> Unit) {
        val consumer = Consumer<E> { listener(it) }
        context.plugins.pipelines.add(EventListener(type, consumer, EventPriority.NORMAL, InteractionPolicy.STANDARD_SIZE_BIF))
    }

    fun reset() {
        GameTicks.dropLogoutRequests(world)
        world.players.toList().forEach(world.players::remove)
        world.playerMap.clear()
        world.objects.toList().forEach { world.objects.unregister(it) }
        world.npcs.toList().forEach(world.npcs::remove)
        world.items.toList().forEach { world.items.unregister(it) }
        context.plugins.pipelines.replaceAll(EventListenerPipelineSet())
    }

    private fun build(): LunaContext {
        // Object definitions lock on their first store, and CacheMap stores them, so they come from CacheMap here.
        CacheMap.map
        val context = TestContexts.newBoundContext()
        val cache = context.cache
        cache.open()
        cache.runDecoders(
            context,
            WidgetDefinitionDecoder(),
            ItemDefinitionDecoder(),
            NpcDefinitionDecoder(),
            VarBitDefinitionDecoder(),
            VarpDefinitionDecoder(),
        )
        cache.waitForDecoders()
        listOf(
            EquipmentDefinitionFileParser(),
            MessageRepositoryFileParser(context.server.messageRepository),
            WeaponTypeDefinitionFileParser(),
            WeaponDefinitionFileParser(),
            NpcCombatDefinitionFileParser(),
        ).forEach(Runnable::run)
        cache.setMapIndexTable(openMap())
        context.world.botManager.personalityManager.load()
        // Logout requests only register while the service runs; GameTicks never lets one finish.
        context.world.logoutService.startAsync().awaitRunning()
        GameTicks.adoptCallingThread(context.game)
        return context
    }

    /** Every region of the test area exists with no terrain, so all of it is walkable until a test places objects. */
    private fun openMap(): MapIndexTable {
        val regions = (MIN_X..MAX_X step REGION_SIZE).flatMap { x ->
            (MIN_Y..MAX_Y step REGION_SIZE).map { y -> Position(x, y).region }
        }
        val index = ImmutableMap.copyOf(regions.associateWith { MapIndex(it, 0, 0, false) })
        return MapIndexTable(index, MapObjectSet(ImmutableList.of()), MapTileGridSet(ImmutableMap.of()))
    }
}
