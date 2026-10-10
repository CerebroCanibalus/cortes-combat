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

        // **Lo que el pack declara para este Kind manda sobre el sonido del perfil** (fase 7).
        // Tres campos y tres Kind, y el tercero es el que no parece: `swing` del YAML es el
        // MISS -- literalmente "the swing found nothing", el barrido que no conectó. Los demás
        // Kinds (CHARGE, PARRY, LUNGE...) no tienen campo en el YAML y siguen con los nuestros.
        // El id viaja registrado por WeaponPackSounds, así que el packet al cliente es legal.
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
                    SoundEvent registered = id == null ? null
                            : BuiltInRegistries.SOUND_EVENT.get(id);
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
     * The maza's air smash, played at the thing it landed on.
     *
     * <p><b>Its own method and not a {@link ModSounds.Kind}, and the pitch is the whole reason.</b>
     * Every other sound here is pitched by the weapon and nothing else, which is what
     * {@code ModSounds#pitch} answers. This one is pitched by <em>how far the player fell</em>, and
     * a {@code Kind} would have gone into that ladder, found no branch for itself, and come out at
     * 1.0 -- the exact failure this project documents twice: a sound declared, registered and
     * played, with the pitch quietly the default.</p>
     *
     * <p>Played at the <b>target</b> and not at the attacker, unlike every other hit sound. The
     * anvil is describing the impact, and the impact happened where the weapon landed.</p>
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

    /**
     * The maza's core, layered under its own hammer recording on every hit.
     *
     * <p><b>Vanilla's {@code block.heavy_core.place}, named here and played at a caller-chosen
     * pitch.</b> It is the sound of the heaviest block in the game being placed: a low, dense thud
     * with no ring, which is what a mace landing on a body is missing between the hammer's own
     * recording and the anvil. The pitch is the caller's because the smash drops it -- see
     * {@link ModSounds#MACE_CORE_SMASH_PITCH} -- and a {@code Kind} could not carry that, the same
     * reason {@link #playMaceSmash} is not one.</p>
     *
     * <p>Played at the <b>target</b>, like the smash: the impact is where the weapon landed.</p>
     */
    public static void playHeavyCore(ServerLevel level, Entity at, float pitch) {
        var log = org.slf4j.LoggerFactory.getLogger("cortescombat");
        if (REPORTED.add("block.heavy_core.place")) {
            log.info("[sonido] '{}' pedido, pitch {}", "block.heavy_core.place", pitch);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.HEAVY_CORE_PLACE, SoundSource.PLAYERS, 1.0F, pitch);
    }

    /**
     * The pala's own impact, layered under the borrowed vanilla swing.
     *
     * <p><b>Two recordings and they are the tool's whole voice.</b> The ordinary hit adds
     * {@code block.lantern.break} low; a <em>charged</em> hit adds
     * {@code entity.wind_charge.wind_burst}, lower still, on top of it. The pala has no
     * recordings of its own -- it is a vanilla item whose silhouette we did not draw -- so this is
     * the one place it can sound like a tool rather than like every other unrecorded weapon
     * playing vanilla's weak hit.</p>
     *
     * <p>Played at the <b>target</b>: both describe the impact.</p>
     *
     * @param charged whether the swing was at full charge, which layers the wind burst on
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
                    SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.0F,
                    ModSounds.SHOVEL_CHARGED_PITCH);
        }
        level.playSound(null, at.getX(), at.getY(), at.getZ(),
                SoundEvents.LANTERN_BREAK, SoundSource.PLAYERS, 1.0F,
                ModSounds.SHOVEL_HIT_PITCH);
    }

    /**
     * The warhammer streak's own voice, and it is vanilla's.
     *
     * <p><b>Not one of ours, and that is the point.</b> The escalation is a bare multiplier with no
     * sound bound to it, so this is the only channel the player has for finding out that a streak
     * is running at all -- and a sound the game already ships is a sound the ear is already tuned
     * to.</p>
     *
     * <p><b>The note block, because a pitch ladder is the one thing it was built for.</b> Every
     * other candidate is a hit: short, broadband, and read as impact rather than as pitch, so
     * sliding it from half to double only makes it louder. The note block's own pitch slider runs
     * 0.5 to 2.0 across two octaves, which is exactly the range the streak wanted, and the player
     * has heard that sweep on a note block a thousand times.</p>
     *
     * <p>A {@code Holder.Reference} rather than a bare {@code SoundEvent}, taken straight off
     * {@code SoundEvents}: it already is the registered holder the sound packet wants.</p>
     */
    public static final Holder<SoundEvent> STACK_STEP = SoundEvents.NOTE_BLOCK_PLING;

    /**
     * Quiet, because this layers under a warhammer impact rather than competing with it.
     *
     * <p>Half a note block's own volume: the hit sound already says the hammer landed, and this one
     * only has to say the streak went up.</p>
     */
    public static final float STACK_STEP_VOLUME = 0.5F;

    /**
     * The streak step, heard by the player building the streak and by nobody else.
     *
     * <p><b>One packet rather than a broadcast, and that is a rule and not an optimisation.</b> A
     * pitch ladder is only readable played alone. Broadcast to everyone nearby, two warhammers
     * trading blows produce two interleaved ladders and neither can be followed -- and bystanders
     * hear a stranger's combo count, which is information nobody needs.</p>
     *
     * <p><b>Not {@code level.playSound(player, ...)}.</b> That reads like the private version and is
     * the opposite: it forwards to {@code PlayerList#broadcast}, whose first argument is named
     * {@code except}, and the loop tests {@code serverplayer != except}. Passing the attacker
     * would mute it for the one player it is meant for. Verified in the 1.21.1 sources, not
     * remembered.</p>
     *
     * <p><b>Seed zero, so the same streak always sounds the same.</b> A seeded sound varies per
     * play, which is right for a hit and wrong for an instrument: a ladder that plays a slightly
     * different note each time is not a ladder.</p>
     */
    public static void playStackStep(ServerPlayer listener, Entity at, float pitch) {
        listener.connection.send(new ClientboundSoundPacket(
                STACK_STEP, SoundSource.PLAYERS, at.getX(), at.getY(), at.getZ(),
                STACK_STEP_VOLUME, pitch, 0L));
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