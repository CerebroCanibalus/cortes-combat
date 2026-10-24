package com.cortes.combat.sound;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.weaponpack.ResolvedWeapon;
import com.cortes.combat.weaponpack.WeaponPackStore;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * The 1.20.1 twin of the single place a combat sound is played. Same names, same pitches,
 * two vanilla substitutions.
 *
 * <p>1.20.1 does not ship {@code block.heavy_core.place} (the heavy core is a 1.21 block)
 * or {@code entity.wind_charge.wind_burst} (the wind charge is a 1.21 entity). Each gets
 * the closest thing this version has, and the choice is written down because a silent
 * substitution is how a sound "stops working" without an error:</p>
 * <ul>
 *   <li>Heavy core place becomes {@code ANVIL_PLACE} -- same metal family as the mace's
 *       anvil voice, but the place (not the land) so the two do not_stack on the same
 *       event.</li>
 *   <li>Wind charge burst becomes {@code FIREWORK_ROCKET_BLAST} -- a burst, lower still,
 *       under the same lantern break.</li>
 * </ul>
 * <p>The stack-step packet takes a {@code Holder} here too, wrapped with
 * {@code Holder.direct} around the bare {@code SoundEvent}. (⌒‿⌒)</p>
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
     * @param pitchScale a multiplier on the normal pitch, where 1.0 is unchanged and 0.8 is lower
     */
    public static void play(ServerLevel level, Entity at, WeaponProfile profile,
                            ModSounds.Kind kind, float pitchScale) {
        String name = ModSounds.of(profile, kind);
        SoundEvent event = event(name);

        if (at instanceof net.minecraft.world.entity.LivingEntity living) {
            ResolvedWeapon pack = WeaponPackStore.resolve(living.getMainHandItem());
            if (pack != null) {
                String custom = switch (kind) {
                    case HIT -> pack.hit();
                    case CHARGED_HIT -> pack.charged();
                    case MISS -> pack.swing();
                    default -> null;
                };
                if (custom != null) {
                    ResourceLocation id = ResourceLocation.tryParse(custom);
                    // 1.20.1's registry getter hands back the event itself, and a missing
                    // id must read as "not registered" (checked first) rather than as the
                    // registry's default sound.
                    SoundEvent registered = id != null
                            && BuiltInRegistries.SOUND_EVENT.containsKey(id)
                            ? BuiltInRegistries.SOUND_EVENT.get(id) : null;
                    if (registered != null) {
                        event = registered;
                    } else if (REPORTED.add("pack:" + custom)) {
                        com.cortes.combat.CortesCombat.LOGGER.warn(
                                "[sonido] '{}' del pack declarado pero no registrado -- ¿el "
                                + "archivo quedó fuera del config que corre el server?", custom);
                    }
                }
            }
        }

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
     * The maza's air smash, played at the thing it landed on.
     *
     * @param fallRatio how much of the fall's bonus was earned, 0..1. Zero never reaches here
     */
    public static void playMaceSmash(ServerLevel level, Entity at, float fallRatio) {
        var log = org.slf4j.LoggerFactory.getLogger("cortescombat");
        if (REPORTED.add(ModSounds.SMASH)) {
            log.info("[sonido] '{}' pedido, pitch {} por caida ({} .. {})",
                    ModSounds.SMASH, ModSounds.smashPitch(fallRatio),
                    ModSounds.SMASH_PITCH_HIGH, ModSounds.SMASH_PITCH_LOW);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F,
                ModSounds.smashPitch(fallRatio));
    }

    public static void playHeavyCore(ServerLevel level, Entity at, float pitch) {
        var log = org.slf4j.LoggerFactory.getLogger("cortescombat");
        if (REPORTED.add("block.heavy_core.place")) {
            log.info("[sonido] '{}' pedido, pitch {}", "block.heavy_core.place", pitch);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 1.0F, pitch);
    }

    /**
     * The pala's own impact, layered under the borrowed vanilla swing.
     *
     * @param charged whether the swing was at full charge, which layers the blast on
     */
    public static void playShovelHit(ServerLevel level, Entity at, boolean charged) {
        var log = org.slf4j.LoggerFactory.getLogger("cortescombat");
        if (charged && REPORTED.add("entity.wind_charge.wind_burst")) {
            log.info("[sonido] '{}' pedido, pitch {}", "entity.wind_charge.wind_burst",
                    ModSounds.SHOVEL_CHARGED_PITCH);
        }
        if (REPORTED.add("block.lantern.break")) {
            log.info("[sonido] '{}' pedido, pitch {}", "block.lantern.break",
                    ModSounds.SHOVEL_HIT_PITCH);
        }
        if (charged) {
            level.playSound(null, at.getX(), at.getY(), at.getZ(),
                    SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F,
                    ModSounds.SHOVEL_CHARGED_PITCH);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.LANTERN_BREAK, SoundSource.PLAYERS, 1.0F,
                ModSounds.SHOVEL_HIT_PITCH);
    }

    /**
     * The warhammer streak's own voice, and it is vanilla's.
     *
     * <p>1.20.1 declares this particular field as a holder while its siblings are bare
     * events, so it is unwrapped once here; the packet re-wraps it at the call site
     * through {@code Holder.direct}.</p>
     */
    public static final SoundEvent STACK_STEP = SoundEvents.NOTE_BLOCK_PLING.value();

    /**
     * Quiet, because this layers under a warhammer impact rather than competing with it.
     */
    public static final float STACK_STEP_VOLUME = 0.5F;

    /**
     * The streak step, heard by the player building the streak and by nobody else.
     *
     * <p><b>One packet rather than a broadcast.</b> A pitch ladder is only readable played
     * alone -- see the shared twin for the full note. Seed zero, so the same streak always
     * sounds the same.</p>
     */
    public static void playStackStep(ServerPlayer listener, Entity at, float pitch) {
        listener.connection.send(new ClientboundSoundPacket(
                Holder.direct(STACK_STEP), SoundSource.PLAYERS, at.getX(), at.getY(), at.getZ(),
                STACK_STEP_VOLUME, pitch, 0L));
    }

    /**
     * The sound event for a name, built rather than looked up.
     *
     * <p><b>There is no lookup.</b> See the shared twin: a mod's {@code sounds.json} entries
     * do not go into the registry, and only {@code createVariableRangeEvent} names one the
     * engine finds by id.</p>
     */
    public static SoundEvent event(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            return null;
        }
        return net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
    }
}
