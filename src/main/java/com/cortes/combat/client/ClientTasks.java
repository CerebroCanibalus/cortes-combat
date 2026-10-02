package com.cortes.combat.client;

import net.minecraft.client.Minecraft;
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
        // The sabre's second cut is heard eight ticks after the first, and 1.21.1 cannot schedule a
        // sound, so the delay is held here.
        DelayedSounds.tick();
        // The lunge has no tick here any more. It used to sit in this list setting the player's
        // velocity once per frame from a decaying curve, which meant the client owned the movement
        // tick by tick and the player had no steering against it. It is now one `push` at the press
        // and then the game's own physics, so there is nothing to advance.
        CrosshairFeedback.onTick(ClientCombatState.aiming());
        // Advances the local charge, which the meter and the viewmodel both read.
        //
        // SwingInputHandler.tick reconciles its own swing against the physical button, because it
        // is the one that has to read `holding` before anything else does. The guard's flag has no
        // such ordering constraint, so it is reconciled here instead of inside its own tick.
        GuardInputHandler.reconcileWithTheButton();
        // Before the viewmodel reads it: the lockout bump is edge triggered, so it has to be
        // counting down and not still holding the number the last packet gave it.
        ClientCombatState.tickParry();
        ClientCombatState.tickLunge();
        SwingInputHandler.tick();
        // Last, because it clears the verdict on the press: vanilla can run the attack path more
        // than once in a tick, and every one of those has to see the same verdict. Clearing it
        // anywhere earlier would let the second of them through to a free vanilla attack.
        SwingInputHandler.endTick();
    }
}
