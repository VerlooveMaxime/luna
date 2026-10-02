package io.luna.net.msg.in;

import io.luna.game.event.impl.DesignPlayerEvent;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.net.codec.ByteMessage;
import io.luna.net.codec.MessageType;
import io.luna.net.msg.GameMessage;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DesignPlayerMessageReader}, with the bytes the 377 client sends on Accept (opcode 163).
 */
final class DesignPlayerMessageReaderTest {

    /**
     * Gender, then head, jaw, torso, arms, hands, legs and feet kits, then five colours. The client sends 255 for a
     * part without a kit: women have no jaw in the 377 cache.
     */
    private static final byte[] FEMALE = {1, 45, (byte) 255, 56, 61, 67, 70, 79, 0, 0, 0, 0, 0};

    private static final byte[] MALE = {0, 0, 12, 18, 26, 33, 36, 42, 0, 0, 0, 0, 0};

    private static DesignPlayerEvent decode(byte[] bytes) {
        GameMessage message = new GameMessage(163, MessageType.FIXED, ByteMessage.wrap(Unpooled.wrappedBuffer(bytes)));
        return new DesignPlayerMessageReader().decode(null, message);
    }

    @Test
    void aWomanWithoutAJawIsAValidDesign() {
        assertTrue(PlayerAppearance.isAllValid(decode(FEMALE).getValues()));
    }

    @Test
    void aWomanWithoutAJawIsStoredWithoutABeard() {
        assertEquals(0, decode(FEMALE).getValues()[PlayerAppearance.BEARD]);
    }

    @Test
    void aMansJawIsKept() {
        assertEquals(12, decode(MALE).getValues()[PlayerAppearance.BEARD]);
    }
}
