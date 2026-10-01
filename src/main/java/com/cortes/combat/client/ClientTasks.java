package com.cortes.combat.client;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-side per-tick work that is not input.
 *
 * <p>Kept separate from the input handler so that "something happened this tick" and "a button
 * changed state" do not end up in the same class.</p>
 */
public final class ClientTasks {

    private ClientTasks() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // Ahead of the feedback tick, so a flash that arrived this tick is already counted as
        // one tick old and decays on schedule rather than sitting at full strength for an extra
        // frame.
        CombatAim.update();
        CrosshairFeedback.onTick(ClientCombatState.aiming());
        // Advances the local charge, which the meter and the viewmodel both read.
        //
        // SwingInputHandler.tick reconciles its own swing against the physical button, because it
        // is the one that has to read `holding` before anything else does. The guard's flag has no
        // such ordering constraint, so it is reconciled here instead of inside its own tick.
        GuardInputHandler.reconcileWithTheButton();
        SwingInputHandler.tick();
        // Last, because it clears the verdict on the press: vanilla can run the attack path more
        // than once in a tick, and every one of those has to see the same verdict. Clearing it
        // anywhere earlier would let the second of them through to a free vanilla attack.
        SwingInputHandler.endTick();
    }
}
