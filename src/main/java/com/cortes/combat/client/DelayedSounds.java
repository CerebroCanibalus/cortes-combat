package com.cortes.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Sounds the server asked for that are not due yet.
 *
 * <p>A list of a handful of shorts, drained one per client tick. It exists because 1.21.1 has no
 * way to schedule a sound: {@code ClientboundSoundPacket} has no delay field and there is no delayed
 * task queue on the server. The sabre's second cut is the only thing that needs one.</p>
 *
 * <p><b>A list rather than a single field</b>, because two of these can be outstanding at once and a
 * single field would have the second overwrite the first. That is not hypothetical: the sabre's own
 * doubled swing sends one, and a second swing from another source in the same tick would collide
 * with it.</p>
 *
 * <p><b>Dropped rather than played late</b> if the player logs out in between, because a sound that
 * arrives when the world the player left is still audible is worse than no sound at all. The queue
 * is client-side and dies with the session, which is the whole reason it lives here.</p>
 */
public final class DelayedSounds {

    /** One pending sound. */
    private record Pending(SoundEvent event, SoundSource source, float volume, float pitch,
                           int ticksLeft) {
    }

    private static final List<Pending> QUEUE = new ArrayList<>();

    private DelayedSounds() {
    }

    /**
     * Queues a sound, if the name resolves.
     *
     * <p>Silently ignoring an unresolvable name is the same choice {@code CombatSounds} makes, and
     * for the same reason: a null event would throw on the client tick rather than at the point of
     * the mistake, which is the wrong place to find out.</p>
     */
    public static void queue(String name, SoundSource source, float volume, float pitch,
                             int delayTicks) {
        if (name == null || name.isBlank() || delayTicks <= 0) {
            return;
        }
        SoundEvent event = com.cortes.combat.sound.CombatSounds.event(name);
        if (event == null) {
            return;
        }
        QUEUE.add(new Pending(event, source, volume, pitch, delayTicks));
    }

    /**
     * Plays whatever is due, once per client tick.
     *
     * <p><b>Builds a new list rather than mutating the one being walked.</b> The first version
     * iterated with an {@code Iterator}, called {@code remove} on the element it was standing on and
     * then {@code add}ed the replacement -- and adding to an {@code ArrayList} bumps its modCount, so
     * the iterator threw {@code ConcurrentModificationException} on the very next step. It crashed
     * the client on the first charged hit, which is the first time the sabre's second sound exists.</p>
     *
     * <p>A tick's worth of sounds is a list of three or four shorts. Rebuilding it costs one
     * allocation per tick while the queue is non-empty and nothing at all when it is empty, and it
     * cannot throw. The alternative -- an index loop -- reads correctly too and is what this would
     * become if the queue ever got large enough for the copy to matter.</p>
     */
    public static void tick() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            QUEUE.clear();
            return;
        }
        List<Pending> carried = new ArrayList<>(QUEUE.size());
        for (int i = 0; i < QUEUE.size(); i++) {
            Pending pending = QUEUE.get(i);
            int left = pending.ticksLeft() - 1;
            if (left > 0) {
                carried.add(new Pending(pending.event(), pending.source(), pending.volume(),
                        pending.pitch(), left));
            } else {
                play(player, pending);
            }
        }
        QUEUE.clear();
        QUEUE.addAll(carried);
    }

    private static void play(Player player, Pending pending) {
        // The sound is bound to the player's eye rather than to a block, so it tracks them if they
        // move during the wait -- the sabre's player is usually still walking away from the swing.
        Minecraft.getInstance().getSoundManager().play(
                new EntityBoundSoundInstance(
                        pending.event(),
                        pending.source(),
                        pending.volume(),
                        pending.pitch(),
                        player,
                        player.getId()));
    }

    /** Whether anything is waiting. Read by tests and by the debug overlay. */
    public static int pending() {
        return QUEUE.size();
    }
}