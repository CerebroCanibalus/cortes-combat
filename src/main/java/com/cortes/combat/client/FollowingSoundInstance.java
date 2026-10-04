package com.cortes.combat.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * A sound that stays on the player who made it, and that the client can put a hand on.
 *
 * <p><b>Two things at once, and both are the same object.</b> Following the player and being
 * stoppable are not two features: the only reason either is possible is that the sound was started
 * <em>on the client</em> instead of being broadcast from the server, so one object on this side
 * knows both where the sound is and that it exists.</p>
 *
 * <p><b>Why the server could not do either.</b> {@code ServerLevel#playSound} hands the sound to
 * {@code PlayerList#broadcast} and forgets it -- there is no list of live sounds on the server, so
 * there is nothing to send a stop to. And the packet it sends carries <em>coordinates</em>, not an
 * entity, so the sound is pinned to the spot it was played from and stays there while the player
 * walks away. Both are architecture, not a missing method, and both were read in the 1.21.1
 * sources rather than remembered.</p>
 *
 * <p><b>The coordinates are updated every tick, not set once.</b> {@code AbstractSoundInstance}
 * keeps {@code x}, {@code y} and {@code z} as plain protected fields, which is what makes this
 * possible at all -- a sound that was given a position keeps it forever otherwise. Reading the
 * player's position on construction would put the sound where the player was standing when the
 * reload started, which is the bug being fixed.</p>
 *
 * <p><b>Cuts dead, with no fade.</b> {@link #stop()} flips a flag the instance reads and the client
 * acts on; the sound dies on the next tick rather than over a ramp. That is what a cancelled
 * action should sound like: the moment the player let go is the moment it stops.</p>
 */
public final class FollowingSoundInstance extends AbstractTickableSoundInstance {

    private final Player follow;

    /**
     * @param soundEvent the event, built by name -- a mod event is not in the registry
     * @param pitch      as written by {@code ModSounds#pitch}, and nothing else decides it
     * @param follow     the player whose position the sound tracks
     */
    public FollowingSoundInstance(SoundEvent soundEvent, float pitch, Player follow) {
        // The seed is zero on purpose: the same press always sounds the same, and a reload the
        // player can hear twice is not a hit they are learning the pitch of.
        super(soundEvent, SoundSource.PLAYERS, RandomSource.create(0L));
        this.follow = follow;
        this.pitch = pitch;
        this.volume = 1.0F;
        // LINEAR, not NONE. NONE would pin the sound to the listener and ignore the position
        // entirely, which makes following the player unnecessary by deleting the question -- and
        // it also means the sound cannot be heard as coming from anywhere. Following is the
        // question that was asked; this is the answer to it.
        this.attenuation = SoundInstance.Attenuation.LINEAR;
    }

    @Override
    public void tick() {
        if (isStopped()) {
            return;
        }
        if (follow == null || follow.isRemoved()) {
            stop();
            return;
        }
        // The three fields are read by the audio thread when it recomputes the gain, and they are
        // plain doubles: a tear-free write on a 64 bit JVM is a single store, which is why this is
        // a plain assignment and not a volatile dance. Nothing here needs more than that.
        this.x = follow.getX();
        this.y = follow.getY();
        this.z = follow.getZ();
    }

    /**
     * Ends this sound at the next tick, dead rather than faded.
     *
     * <p>Vanilla's own {@code stop()}, which this exposes rather than reimplements: it is
     * {@code protected final} on {@code AbstractTickableSoundInstance}, so the flag it flips is the
     * one {@code isStopped()} already reads. A second flag here would have been a second answer to
     * "is this sound still going", and the two could disagree.</p>
     *
     * <p>Called from the same place the player lets go of the button, so the sound ends on the
     * same tick the decision to stop reloading is made. A ramp would be the wrong shape: this is a
     * mechanic being cancelled, not a machine powering down.</p>
     */
    public void cut() {
        stop();
    }
}