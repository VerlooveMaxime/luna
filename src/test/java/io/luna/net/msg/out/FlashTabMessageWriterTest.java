package io.luna.net.msg.out;

import io.luna.game.model.mob.overlay.GameTabSet.TabIndex;
import io.luna.net.msg.GameMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link FlashTabMessageWriter}, against what the 377 client reads for opcode 238.
 */
final class FlashTabMessageWriterTest {

    @Test
    void usesTheFlashOpcode() {
        GameMessage message = new FlashTabMessageWriter(TabIndex.INVENTORY).toGameMessage(null);
        try {
            assertEquals(238, message.getOpcode());
        } finally {
            message.getPayload().releaseAll();
        }
    }

    @Test
    void sendsTheTabIndexAsAPlainByte() {
        GameMessage message = new FlashTabMessageWriter(TabIndex.SKILL).toGameMessage(null);
        try {
            assertEquals(1, message.getPayload().getBuffer().readableBytes());
            assertEquals(TabIndex.SKILL.getIndex(), message.getPayload().getBuffer().getByte(0));
        } finally {
            message.getPayload().releaseAll();
        }
    }
}
