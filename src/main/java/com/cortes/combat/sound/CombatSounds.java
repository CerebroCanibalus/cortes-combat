package com.cortes.combat.sound;

import com.cortes.combat.core.weapon.WeaponProfile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The one place a combat sound is actually played.
 *
 * <p>{@link ModSounds} decides <em>what</em> and {@code sounds.json} decides which of three files
 * answers a name; this only looks the name up and hands it to the level.</p>
 *
 * <p><b>Server side, and that is the decision with a cost.</b> Everything here happens where the
 * swing is resolved, so a sound is played once per event that actually happened and never for one
 * that did not. The price is a round trip of latency before the player hears their own hit, which
 * on a swing that already has a viewmodel and a particle is invisible. The alternative -- playing
 * it on the client at the press -- is instant and wrong in the two cases that matter: the swing
 * found nothing, and the swing hit something the server then refused.</p>
 */
public final class CombatSounds {

    private CombatSounds() {
    }

    private static final java.util.Set<String> REPORTED = new java.util.HashSet<>();

    public static void play(ServerLevel level, Entity at, WeaponProfile profile, ModSounds.Kind kind) {
        play(level, at, profile, kind, 1.0F);
    }

    /**
     * The same sound, played at a multiple of its own pitch.
     *
     * <p>For a sequence where each one has to sound later than the last -- the ropera's lunge
     * passing through a group, where a rising series says "this went through four things" in a way
     * that four identical hits do not.</p>
     *
     * <p><b>A multiplier of the profile's pitch, not an absolute.</b> The shotel plays its borrowed
     * recordings at 1.2, and a lunge that overrode that with an absolute number would play the
     * shotel's hit at 1.0 and quietly un-sharpen it. Asked as "this much lower" instead, the
     * weapon's own character survives the adjustment.</p>
     *
     * @param pitchScale a multiplier on the normal pitch, where 1.0 is unchanged and 0.8 is lower
     */
    public static void play(ServerLevel level, Entity at, WeaponProfile profile,
                            ModSounds.Kind kind, float pitchScale) {
        String name = ModSounds.of(profile, kind);
        SoundEvent event = event(name);

        // One line per event per session, because the alternative is silence with nothing to read.
        //
        // "Does not exist, cannot add it to event" in the log proves the FILE loaded. It says
        // nothing about whether the event ended up registered under the name this class builds, and
        // a name that resolves to null is exactly as silent as one that does not. Those two are
        // the same symptom from the outside and only one line of log tells them apart.
        var log = org.slf4j.LoggerFactory.getLogger("cortescombat");
        if (REPORTED.add(name)) {
            log.info("[sonido] '{}' pedido, pitch {}", name, ModSounds.pitch(profile, kind));
        }

        if (event == null) {
            return;
        }
        float pitch = ModSounds.pitch(profile, kind) * pitchScale;
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                event, SoundSource.PLAYERS, 1.0F, pitch);
    }

    /**
     * The sound event for a name, built rather than looked up.
     *
     * <p><b>There is no lookup.</b> A mod's {@code sounds.json} entries do not go into
     * {@code BuiltInRegistries.SOUND_EVENT}: {@code SoundManager} keeps them in a private
     * {@code Map<ResourceLocation, WeighedSoundEvents>} and the engine resolves them at playback.
     * Only vanilla's own events are in the registry, so asking it for
     * {@code cortescombat:sword_hit} returns null forever, for every mod sound, and the symptom is
     * total silence with a build that passes.</p>
     *
     * <p>{@code createVariableRangeEvent} is the supported way to name one, and the engine finds it
     * by id the same way it finds vanilla's. A name with no file behind it is then silence at
     * playback rather than an exception here, which is the right place for that failure to live.</p>
     */
    public static SoundEvent event(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return null;
        }
        return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
    }
}