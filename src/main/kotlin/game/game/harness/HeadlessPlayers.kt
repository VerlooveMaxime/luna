package game.harness

import io.luna.LunaContext
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerCredentials
import io.luna.game.persistence.PlayerData
import java.util.concurrent.CompletableFuture

private val HEADLESS_NAME = Regex("^[a-z0-9_]{1,${HarnessConfig.MAX_USERNAME_LENGTH}}$")

/** Lower-cases [name] and checks it against the client's username rules and the configured [prefix]. */
fun requireHeadlessName(name: String, prefix: String): String {
    val username = name.trim().lowercase()
    if (!HEADLESS_NAME.matches(username)) {
        throw HarnessException(
            400,
            "'$name' is not a valid name: 1 to ${HarnessConfig.MAX_USERNAME_LENGTH} letters, digits or underscores",
        )
    }
    if (!username.startsWith(prefix)) {
        throw HarnessException(400, "headless player names must start with '$prefix'")
    }
    return username
}

val Player.isHeadless: Boolean
    get() = client is RecordingGameClient

/**
 * Logs headless players in and out the way `Bot.login` does: load the save, register, go active.
 * Going active posts `LoginEvent`, so content (starter kit, tabs) sees an ordinary login.
 * Every method runs on the game thread.
 */
class HeadlessPlayers(private val context: LunaContext, private val config: HarnessConfig) {

    private val world = context.world

    /** Completes on the game thread once the player is in the world. */
    fun login(name: String): CompletableFuture<Player> {
        val username = requireHeadlessName(name, config.playerNamePrefix)
        requireLoginPossible(username)
        val player = Player(context, PlayerCredentials(username, config.playerPassword))
        val log = MessageLog(config.messageBufferSize) { world.currentTick }
        player.setClient(RecordingGameClient(player, context.server.messageRepository, log))
        return world.persistenceService.load(username)
            .thenApplyAsync({ data -> enterWorld(player, data) }, context.game.gameExecutor)
    }

    fun logout(player: Player) {
        val client = player.client as? RecordingGameClient
            ?: throw HarnessException(409, "${player.username} is not a headless player; only those log out here")
        client.isForcedLogout = true
        client.sendLogoutRequest()
    }

    private fun enterWorld(player: Player, data: PlayerData?): Player {
        // Checked again: another login or a logout may have happened while the save was loading.
        requireLoginPossible(player.username)
        player.loadData(data)
        if (!world.players.add(player)) {
            throw HarnessException(503, "the world refused ${player.username}")
        }
        player.state = EntityState.ACTIVE
        return player
    }

    private fun requireLoginPossible(username: String) {
        when {
            world.isFull -> throw HarnessException(503, "the world is full")
            world.bots.exists(username) -> throw HarnessException(409, "$username is a bot's name")
            world.playerMap.containsKey(username) -> throw HarnessException(409, "$username is already online")
            world.logoutService.isSavePending(username) ->
                throw HarnessException(409, "$username is still being saved; retry in a moment")
        }
    }
}
