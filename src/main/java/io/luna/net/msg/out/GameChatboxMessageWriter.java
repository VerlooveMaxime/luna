package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that sends a game message.
 *
 * @author lare96
 */
public final class GameChatboxMessageWriter extends GameMessageWriter {

    /**
     * Ends a debugging line. The IdleRS client strips it and never lets such a line cover the tutorial's help box with
     * "Click here to continue" (client class {@code idlers.ChatMessages}).
     */
    public static final String DEBUG_SUFFIX = ":debug:";

    /**
     * The message.
     */
    private final Object message;

    /**
     * Creates a new {@link GameChatboxMessageWriter}.
     *
     * @param message The message.
     */
    public GameChatboxMessageWriter(Object message) {
        this.message = message;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(63, MessageType.VAR, buffer);
        msg.putString(message.toString());
        return msg;
    }

    /**
     * @return The message.
     */
    public Object getMessage() {
        return message;
    }
}
