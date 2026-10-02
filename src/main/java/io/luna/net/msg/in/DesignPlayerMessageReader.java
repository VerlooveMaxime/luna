package io.luna.net.msg.in;

import io.luna.game.event.impl.DesignPlayerEvent;
import io.luna.game.model.mob.Player;
import io.luna.game.model.mob.block.PlayerAppearance;
import io.luna.game.model.mob.block.PlayerAppearance.DesignPlayerInterface;
import io.luna.net.msg.GameMessage;
import io.luna.net.msg.GameMessageReader;

/**
 * A {@link GameMessageReader} implementation that intercepts data sent when the Player uses the character
 * design screen.
 *
 * @author lare96
 */
public final class DesignPlayerMessageReader extends GameMessageReader<DesignPlayerEvent> {

    /**
     * The value the client sends for a body part without an identity kit.
     */
    private static final int NO_KIT = -1;

    @Override
    public DesignPlayerEvent decode(Player player, GameMessage msg) {
        int gender = msg.getPayload().get();
        byte[] models = msg.getPayload().getBytes(7);
        byte[] colors = msg.getPayload().getBytes(5);
        int[] values = new int[13];
        int index = 0;
        values[index++] = gender;
        for (int model : models) {
            values[index++] = model;
        }
        for (int color : colors) {
            values[index++] = color;
        }
        // The 377 cache has no jaw for women, so the client sends -1 ("no kit"); Luna's "no beard" is 0.
        if (gender == PlayerAppearance.GENDER_FEMALE && values[PlayerAppearance.BEARD] == NO_KIT) {
            values[PlayerAppearance.BEARD] = 0;
        }
        return new DesignPlayerEvent(player, gender, models, colors, values);
    }

    @Override
    public boolean validate(Player player, DesignPlayerEvent event) {
        return player.getOverlays().has(DesignPlayerInterface.class);
    }
}