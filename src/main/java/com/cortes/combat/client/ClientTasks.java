package com.cortes.combat.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side per-tick work that is not input.
 *
 * <p>Kept separate from the charge input handler so that "something happened this tick" and
 * "a button changed state" do not end up in the same class.</p>
 */
public final class ClientTasks {

    private ClientTasks() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ChargeInputHandler.tickPrediction();
        // Ahead of the feedback tick, so a flash that arrived this tick is already counted as
        // one tick old and decays on schedule rather than sitting at full strength for an extra
        // frame.
        CombatAim.update();
        CrosshairFeedback.onTick(ClientCombatState.aiming());
    }
}
