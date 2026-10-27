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
        // **Every other player's clock, once per game tick.**
        //
        // Before the local body's pose, and for the same reason that one is last: the pose is read
        // from the clock's frame, so the frame has to have been advanced first. Each remote runs the
        // same `ViewModelMotion` from the state the server sent it, driven here rather than where
        // the packet arrived -- a packet is a moment, and the animation is every tick after it.
        //
        // **It is not the local player.** `ItemViewModel.MOTION` is that one, and stepping it from
        // two places would draw the local swing at double speed for the person doing it and at
        // normal speed for everybody watching.
        RemotePoseStates.tick();
        // **El sonido de una carga de boton derecho, siguiendo el estado y no el evento.** Arranca
        // con la carga y se CORTA cuando la carga se corta, que es la mitad del aviso: un sonido que
        // sigue despues de soltar dice lo contrario de lo que paso. Ver `SpecialChargeAudio`.
        SpecialChargeAudio.tick(net.minecraft.client.Minecraft.getInstance());
        // The sabre's second cut is heard eight ticks after the first, and 1.21.1 cannot schedule a
        // sound, so the delay is held here.
        DelayedSounds.tick();
        // The lunge has no tick here any more. It used to sit in this list setting the player's
        // velocity once per frame from a decaying curve, which meant the client owned the movement
        // tick by tick and the player had no steering against it. It is now one `push` at the press
        // and then the game's own physics, so there is nothing to advance.
        CrosshairFeedback.onTick(ClientCombatState.aiming());
        // **El reloj de inactividad del HUD, con la misma definicion de "pelear" en las dos
        // patas.** Actividad = objetivo en el cono, un flash que acaba de llegar (golpe) o una
        // carga en curso. No cuenta caminar ni girar: si contara, las barras nunca se irian.
        HudIdle.onTick(ClientCombatState.aiming()
                || CrosshairFeedback.active()
                || ClientCombatState.chargeRatio() > 0.0F
                || ClientCombatState.specialCharge() > 0.0F);
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
        // **After the swing handler**, and that order is the whole thing: the body's poses read
        // the viewmodel's frame, so the frame has to have been advanced first. Putting this above
        // `SwingInputHandler.tick` would draw the arms one tick behind the weapon, which on a
        // three-second axe charge is a charge bar and an arm that disagree for three seconds.
        com.cortes.combat.thirdperson.BodyPoseDispatcher.tick(Minecraft.getInstance().player);
        // **El zumbido de una flecha que pasa al lado.** Escanea cada tres ticks y no cada tick, por
        // la leccion del reactor -- ver `ArrowWhizz`.
        ArrowWhizz.tick(Minecraft.getInstance());
        // Last, because it clears the verdict on the press: vanilla can run the attack path more
        // than once in a tick, and every one of those has to see the same verdict. Clearing it
        // anywhere earlier would let the second of them through to a free vanilla attack.
        SwingInputHandler.endTick();
    }
}
