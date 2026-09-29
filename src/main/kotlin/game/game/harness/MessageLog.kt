package game.harness

import io.luna.net.client.GameClient
import io.luna.net.msg.GameMessage
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.NpcUpdateMessageWriter
import io.luna.net.msg.out.PlayerUpdateMessageWriter
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/** The log `/messages` reads: a headless client keeps its own, a real client's is in the tap on its channel. */
fun messageLogOf(client: GameClient): MessageLog? =
    if (client is RecordingGameClient) client.log else MessageTap.of(client.channel)?.log

/** One outgoing message as a client would have received it; [fields] are the writer's plain-valued fields. */
data class RecordedMessage(val seq: Long, val tick: Long, val type: String, val fields: Map<String, Any>)

/**
 * The last [capacity] messages the server sent to one player, numbered so a reader can poll for what is new.
 *
 * Player and NPC updating are skipped: they are sent every tick and would push everything else out.
 * Messages arrive from the game thread, the player-updating threads and Netty's event loop, hence the locking.
 */
class MessageLog(private val capacity: Int, private val currentTick: () -> Long) {

    private val entries = ArrayDeque<RecordedMessage>()
    private var nextSeq = 1L

    init {
        require(capacity > 0) { "capacity must be positive, got $capacity" }
    }

    /** A headless player's message, read from the writer itself. */
    fun record(writer: GameMessageWriter) {
        if (writer is PlayerUpdateMessageWriter || writer is NpcUpdateMessageWriter) {
            return
        }
        append(readableName(writer.javaClass), plainFields(writer))
    }

    /** A real client's message, read back from its encoded form by [EncodedMessageDecoder]. */
    fun record(message: GameMessage) {
        EncodedMessageDecoder.decode(message)?.let { append(it.type, it.fields) }
    }

    private fun append(type: String, fields: Map<String, Any>) {
        val tick = currentTick()
        synchronized(this) {
            if (entries.size == capacity) {
                entries.removeFirst()
            }
            entries.addLast(RecordedMessage(nextSeq++, tick, type, fields))
        }
    }

    /** Messages numbered above [since] whose type contains [type] (any type when `null`), oldest first. */
    fun since(since: Long, type: String? = null): MessagesView =
        synchronized(this) {
            val matching = entries.filter { it.seq > since && matchesType(it, type) }
            MessagesView(next = nextSeq - 1, messages = matching)
        }

    private fun matchesType(message: RecordedMessage, type: String?): Boolean =
        type == null || message.type.contains(type, ignoreCase = true)

    private companion object {

        /** Primitive, string and enum fields only, so a writer holding a player or a container never drags it in. */
        fun plainFields(writer: GameMessageWriter): Map<String, Any> =
            generateSequence<Class<*>>(writer.javaClass) { it.superclass }
                .takeWhile { it != GameMessageWriter::class.java }
                .flatMap { it.declaredFields.asSequence() }
                .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
                .mapNotNull { field -> plainValue(field, writer)?.let { field.name to it } }
                .toMap()

        private fun plainValue(field: Field, owner: Any): Any? {
            field.setAccessible(true)
            return when (val value = field.get(owner)) {
                is Number, is Boolean, is Char -> value
                is CharSequence -> value.toString()
                is Enum<*> -> value.name
                else -> null
            }
        }
    }
}
