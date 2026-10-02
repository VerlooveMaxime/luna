package io.luna.net.msg.out;

import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex;
import io.luna.net.codec.ByteMessage;
import io.luna.net.msg.GameMessageWriter;
import io.netty.buffer.ByteBuf;

/**
 * A {@link GameMessageWriter} implementation that makes a {@link TabIndex} flash until the player clicks it. The tab
 * must have an interface; if it is the open tab, the client switches to another one so the player has to click it.
 *
 * @author lare96
 */
public final class FlashTabMessageWriter extends GameMessageWriter {

    /**
     * The tab to flash.
     */
    private final TabIndex tab;

    /**
     * Creates a new {@link FlashTabMessageWriter}.
     *
     * @param tab The tab to flash.
     */
    public FlashTabMessageWriter(TabIndex tab) {
        this.tab = tab;
    }

    @Override
    public ByteMessage write(Player player, ByteBuf buffer) {
        ByteMessage msg = ByteMessage.message(238, buffer);
        msg.put(tab.getIndex());
        return msg;
    }
}
