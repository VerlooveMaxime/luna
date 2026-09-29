package game.harness

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.io.BotChannel
import io.luna.net.client.GameClient
import io.luna.net.msg.GameMessageRepository
import io.luna.net.msg.GameMessageWriter

/**
 * Client of a headless player: no socket, every outgoing message is recorded in [log].
 *
 * Messages are still encoded by [GameClient.queue], which then discards them because [BotChannel] is never active.
 * Encoding matters: player and NPC updating track the local mob lists while writing, as for a real client.
 */
class RecordingGameClient(
    player: Player,
    repository: GameMessageRepository,
    val log: MessageLog,
) : GameClient(BotChannel.CHANNEL, repository, player) {

    override fun queue(writer: GameMessageWriter) {
        log.record(writer)
        super.queue(writer)
    }
}
