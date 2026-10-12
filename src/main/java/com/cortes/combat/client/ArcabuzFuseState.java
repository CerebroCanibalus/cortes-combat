package com.cortes.combat.client;

import com.cortes.combat.state.ArcabuzState;

/**
 * What the client knows about the arcabuz, and the only place it is kept.
 *
 * <p><b>A separate file on purpose.</b> The fuse is neither the charge nor the cooldown, so
 * putting it in {@code ClientCombatState} would mean a record that every weapon's draw reads
 * three numbers it does not use, plus a second copy of the fuse's length in the state sync's
 * place. This is three numbers and one rule.</p>
 *
 * <p><b>The client counts down from what the server sent and the server still owns the shot.</b>
 * The server sends two packets per shot -- lit, then gone -- and the countdown in between runs
 * on the client's own clock. That is the same trade {@code ClientCombatState} makes for the
 * parry's five second lockout, and it is what keeps a shot at two packets instead of twenty.</p>
 *
 * <p>The drift is handled the way the parry's is: a correction from the server that arrives
 * higher than the local count wins, because a number that jumped up is a new fuse rather than a
 * late packet. Without that, a one tick jitter at the end looks exactly like a press and the
 * client restarts a fuse that has already gone off -- which is the artefact of
 * {@code observeStagger}, mirrored.</p>
 */
public final class ArcabuzFuseState {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("cortescombat");

    private static volatile int fuseTicks = 0;
    private static volatile int reloadTicks = 0;
    /**
     * La recarga que el servidor mandó en el press, para el denominador del progreso.
     *
     * <p><b>Se recibe y no se lee de la constante, y esa es la corrección.</b> El contador se
     * envejece solo desde el número que llegó; dividirlo por {@code ArcabuzState.RELOAD_TICKS}
     * funcionaba sólo mientras todas las recargas duraban lo mismo. Con Quick Charge la barra
     * arrancaría a un 20 % de progreso en un arma que todavía no empezó a cargar.</p>
     */
    private static volatile int reloadTotal = 0;
    private static volatile boolean loaded = false;

    private ArcabuzFuseState() {
    }

    /**
     * What the server said, and the two states this used to get wrong.
     *
     * <p><b>{@code loaded} survives the lit packet, and that is the whole of "apunta hacia
     * abajo".</b> This method used to answer {@code lit} with {@code loaded = false}, on the
     * reasoning that lighting the fuse spends the firestone. It does -- in the chamber. But the
     * playtest is what the player sees, and for a full second of burn the gun dropped its loaded
     * pose, pointed at the floor, and then sprang back when the shot went off. Two channels moving
     * in opposite directions over the same second is also what read as the model bobbing.</p>
     *
     * <p>So the charge is in the barrel until the barrel empties, and the packet that says the gun
     * went off is the one that clears it.</p>
     *
     * <p><b>The reload's length arrives here rather than being counted locally.</b> A client that
     * started its own count when the packet landed would draw a motion that is only as long as the
     * latency, which on a button the player is holding for fifty ticks is a motion of no length at
     * all.</p>
     */
    public static void accept(boolean lit, int ticks, boolean hasFirestone, int reloading) {
        reloadTicks = Math.max(reloading, 0);
        reloadTotal = reloadTicks;
        // **Every packet the client receives, with all four numbers, because the alternative is
        // guessing.**
        //
        // This file is the client's whole knowledge of the gun and it had no logging at all, so a
        // playtest reporting "the model is always the loaded one" produced a log with zero client
        // lines in it -- the same blind spot `/cc input` was built for on the input side. Two
        // rounds of diagnosis went into vanilla's override resolution and into the server's state
        // before anyone asked whether the four numbers arriving here were the ones expected, and
        // that question costs one line.
        LOG.debug("[arcabuz/cliente] paquete lit={} ticks={} firestone={} recargando={}"
                + "  (antes: mecha={} cargado={})",
                lit, ticks, hasFirestone, reloadTicks, fuseTicks, loaded);
        if (lit) {
            fuseTicks = Math.max(ticks, 0);
            // **Kept, and this is the line that changed.** The charge is still in the chamber.
            loaded = true;
            return;
        }
        fuseTicks = 0;
        loaded = hasFirestone;
    }

    /**
     * One client tick: the countdown runs down here rather than on the server's.
     *
     * <p>The server's own countdown lives in {@code ArcabuzState#step} and is the one that
     * decides whether the shot goes off; this one only decides what the player sees, which is
     * why a client that is behind reads a fuse that has already finished.</p>
     */
    public static void tick() {
        if (fuseTicks > 0) {
            fuseTicks--;
        }
        if (reloadTicks > 0) {
            reloadTicks--;
        }
        // **The reload's sound is owned by the reload, so it is cut by the reload not running.**
        //
        // Written here, in the one method that runs every client tick, rather than in each of the
        // three places a reload can end -- completing, being cancelled by the release, and being
        // snuffed because the gun left the hand. All three arrive here as the same fact, and a
        // sound that outlives its reload is the bug this removes.
        if (reloadTicks > 0) {
            if (reloadSound == null) {
                startReloadSound(com.cortes.combat.sound.ModSounds.pitch(
                        com.cortes.combat.core.weapon.WeaponProfile.ARCABUZ,
                        com.cortes.combat.sound.ModSounds.Kind.RELOAD_START));
            }
        } else {
            stopReloadSound();
        }
        // **The chamber is latched here, once per tick, and nowhere else.**
        //
        // The data component on the stack is the server's truth about the firestone -- it is what
        // makes a second arcabuz empty when you swap to it -- but reading it from inside the
        // `ItemProperties` predicate does not work, and the reason is the shape of the renderer:
        // **the predicate is called several times per frame with different views of the same slot,
        // and they do not agree.** The log showed `camara=true, camara=false, camara=true,
        // camara=false` on consecutive calls with the server having written the component exactly
        // once, so the flicker was never the server disagreeing with itself -- it was two stacks
        // inside one frame.
        //
        // One read per tick cannot alternate inside a frame, and nothing in the model needs to be
        // more current than that: the chamber changes on the reload completing and on the shot, and
        // both already cost a tick to become visible.
        latchChamber();
    }

    private static volatile boolean chamberLatched = false;

    // --- The reload's own sound, and the only sound in the mod the client owns. ---

    private static net.minecraft.client.resources.sounds.SoundInstance reloadSound = null;

    /**
     * Starts the reload's sound, here rather than on the server.
     *
     * <p><b>It has to be here, and that is not a preference.</b> The server plays sounds by
     * broadcasting a packet and then forgetting about it -- {@code PlayerList#broadcast} keeps no
     * list of live sounds -- so there is nothing on the other side to send a stop to. A reload the
     * player abandons would keep sounding to the end of the file no matter what the server
     * decided afterwards. Started here, one object exists and the client can end it.</p>
     *
     * <p>Also follows the player, which the server's path cannot do: the packet it sends carries
     * coordinates, so the sound stays where it was played from.</p>
     */
    public static void startReloadSound(float pitch) {
        stopReloadSound();
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options == null) {
            return;
        }
        var event = com.cortes.combat.sound.CombatSounds.event(
                com.cortes.combat.sound.ModSounds.of(
                        com.cortes.combat.core.weapon.WeaponProfile.ARCABUZ,
                        com.cortes.combat.sound.ModSounds.Kind.RELOAD_START));
        if (event == null) {
            return;
        }
        FollowingSoundInstance sound = new FollowingSoundInstance(event, pitch, mc.player);
        reloadSound = sound;
        mc.getSoundManager().play(sound);
    }

    /**
     * Cuts the reload's sound, dead.
     *
     * <p>Called on every tick the reload is not running, which is what makes it correct rather than
     * merely responsive: the sound is not ended by the release event, it is ended by the absence of
     * the reload. A reload that completes, one that is cancelled and one whose gun left the hand
     * all end the same way, because all three arrive here as "not reloading".</p>
     */
    public static void stopReloadSound() {
        if (reloadSound == null) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().stop(reloadSound);
        }
        reloadSound = null;
    }

    /**
     * Reads the held stack once and remembers the answer for this tick.
     *
     * <p>Absent means empty, and a burning fuse keeps the gun pointed down for the length of the
     * burn -- the chamber is spent at the light, so the pose has to come from somewhere that is
     * not the chamber, or the weapon springs back halfway through the second.</p>
     */
    private static void latchChamber() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            chamberLatched = false;
            return;
        }
        // **El estado del arma, leido del arma.** Antes eran dos fuentes -- el componente de la
        // recamara y el contador del paquete -- y por eso la pose se quedaba pegada en la que
        // recordaba. Ahora el componente dice las dos cosas y el paquete solo acelera lo que cambia.
        com.cortes.combat.state.ArcabuzState st = mc.player.getMainHandItem()
                .get(com.cortes.combat.registry.ModComponents.ARCABUZ);
        chamberLatched = (st != null && st.loaded()) || fuseTicks > 0;
    }

    /**
     * Whether the gun in hand should be drawn in its loaded pose, as of the last client tick.
     *
     * <p><b>The model reads this and never the stack.</b> The stack is the truth about the
     * firestone; this is the truth about the pose, and it is a number that cannot flicker because
     * it is written once per tick instead of once per render call.</p>
     */
    public static boolean chamberVisible() {
        return chamberLatched;
    }

    /** Whether a reload is running on the client's clock right now. */
    public static boolean isReloading() {
        return reloadTicks > 0;
    }

    /**
     * How far through the reload the client is, 0 to 1, with 0 meaning "not loading".
     *
     * <p>Elapsed, for the same reason {@link #progress()} is: every curve in this project's
     * viewmodel is written as progress, where {@code f(0)} is the rest pose and {@code f(1)} is
     * the end. Returning the countdown would make this the one channel that runs backwards.</p>
     */
    public static float reloadProgress() {
        if (reloadTicks <= 0) {
            return 0.0F;
        }
        int total = Math.max(1, reloadTotal);
        float elapsed = 1.0F - ((float) reloadTicks / (float) total);
        return Math.max(0.0F, Math.min(1.0F, elapsed));
    }

    /** Whether a fuse is burning on the client's clock right now. */
    public static boolean isBurning() {
        return fuseTicks > 0;
    }

    /**
     * Ticks of fuse left, or zero.
     *
     * <p>This is the number the four arcabuz viewmodel states read: idle while zero, the lit
     * animations while it counts down. It is exposed rather than recomputed from a texture's
     * frame index because the server's clock is the one that fires the shot.</p>
     */
    public static int fuseTicks() {
        return fuseTicks;
    }

    /**
     * How far through the fuse the client is, 0 to 1, with 0 meaning "not burning".
     *
     * <p>Elapsed rather than remaining, because every other ramp in this project's viewmodel is
     * written as progress: a curve's {@code f(0)} is the rest pose and {@code f(1)} is the end.
     * Returning the countdown here would make the arcabuz the one channel in the mod that runs
     * backwards.</p>
     *
     * <p><b>{@code totalTicks} entra como argumento, y era el numero escondido de Mecha Corta.</b>
     * Esto dividia por la constante {@code ArcabuzState.FUSE_TICKS}, asi que con una mecha de diez
     * ticks el progreso llegaba a 0,5 cuando la mecha se apagaba: el frame se quedaba a mitad de
     * camino y el disparo salia con la animacion a medio quemar. El total lo calcula el predicado
     * desde el stack -- que es quien sabe que encantamiento tiene el arma -- y aca solo se divide.</p>
     */
    public static float progress(int totalTicks) {
        if (fuseTicks <= 0 || totalTicks <= 0) {
            return 0.0F;
        }
        float elapsed = 1.0F - ((float) fuseTicks / (float) totalTicks);
        return Math.max(0.0F, Math.min(1.0F, elapsed));
    }

    /** Whether the client believes a firestone is in the chamber. */
    public static boolean isLoaded() {
        return loaded;
    }

    /** Everything back to nothing, for a world change or a fresh session. */
    public static void clear() {
        fuseTicks = 0;
        reloadTicks = 0;
        reloadTotal = 0;
        loaded = false;
    }
}
