package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.state.ArcabuzState;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.LungeState;
import com.cortes.combat.state.StreakState;
import com.cortes.combat.state.SyncState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.LinkedHashSet;
import java.util.Set;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * All combat state, as attachments rather than mixin fields.
 *
 * <p>This is the single most load-bearing decision in the project. A mutable field on a mixin
 * does not serialise, does not clear on death, and is not copied on respawn. An attachment
 * has a lifecycle defined by NeoForge, so state survives exactly as long as it should.</p>
 *
 * <p>Which of these sync to the client, and which are client-predicted, is decided per
 * attachment: the server owns anything that affects damage, cooldown or guard. See
 * ARCHITECTURE.md section 1.</p>
 */
public final class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    CortesCombat.MODID);

    /** Charge accumulated while the attack button is held. Predicted on the client, owned by the server. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChargeState>> CHARGE =
            ATTACHMENT_TYPES.register("charge", () -> AttachmentType.builder(() -> ChargeState.EMPTY)
                    .serialize(ChargeState.CODEC)
                    .copyOnDeath()
                    .build());

    /**
     * Remaining guard hits. The maximum comes from the item, not from here.
     *
     * <p><b>No {@code copyOnDeath}, and that is the rule, not an omission.</b> The playtest asked
     * what happens on respawn and the answer was: you came back with a full guard and a raised
     * one, because {@code held} is a <em>button</em> and it rode through death the same way it
     * rode through a respawn. Input state that survives something the player did not do is a bug
     * that only shows up on a fresh session, which is exactly the shape of the report.</p>
     *
     * <p>So dying costs the guard, points and all, and it comes back on the normal regen like a
     * freshly picked up shield. That also happens to be the honest reading of the design: a
     * player who just lost the fight should not walk out of the respawn screen with the resource
     * the fight was about.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GuardState>> GUARD_STAMINA =
            ATTACHMENT_TYPES.register("guard_stamina", () -> AttachmentType.builder(() -> GuardState.EMPTY)
                    .serialize(GuardState.CODEC)
                    .build());

    /** Server-authoritative attack cooldown. The permission to attack. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CooldownState>> COOLDOWN =
            ATTACHMENT_TYPES.register("cooldown", () -> AttachmentType.builder(() -> CooldownState.READY)
                    .serialize(CooldownState.CODEC)
                    .build());

    /** Warhammer consecutive-hit escalation. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StreakState>> HAMMER_STREAK =
            ATTACHMENT_TYPES.register("hammer_streak", () -> AttachmentType.builder(() -> StreakState.EMPTY)
                    .serialize(StreakState.CODEC)
                    .build());

    /** Sabre parry window and its 5s cooldown. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ParryState>> PARRY =
            ATTACHMENT_TYPES.register("parry", () -> AttachmentType.builder(() -> ParryState.READY)
                    .serialize(ParryState.CODEC)
                    .build());

    /**
     * The ropera's lunge in progress: where the player has been measured to, and how far into the
     * window it is.
     *
     * <p>An attachment and not a static, because a static would be one lunge for the whole server:
     * two players in a world would overwrite each other's positions, and it would survive death and
     * world change. Both of those are the bug this class's siblings already had once.</p>
     *
     * <p>Deliberately not {@code copyOnDeath}. A lunge is a movement in progress and dying ends it;
     * inheriting a lunge would be the same category of bug as the guard's held button surviving.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LungeState>> LUNGE =
            ATTACHMENT_TYPES.register("lunge", () -> AttachmentType.builder(() -> LungeState.IDLE)
                    .serialize(LungeState.CODEC)
                    .build());

    /**
     * The entities this player's last lunge has already hit.
     *
     * <p><b>A separate attachment from {@link #LUNGE} and not a field inside it.</b> This list was
     * in the state, and the state is written twenty times per lunge; a list of ids that grows on
     * every write is twenty packets of integers to the client, for something the client never
     * reads. It is also not serializable, because entity references have no codec and inventing one
     * to persist a set that lives for a single dash would be a worse mistake than not persisting
     * it.</p>
     *
     * <p>Never synchronized for the same reason. {@link Lunge} clears it wherever it clears the
     * lunge, so it outlives the dash and not the player.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Set<LivingEntity>>> LUNGE_HITS =
            ATTACHMENT_TYPES.register("lunge_hits",
                    // The type witness is not decoration. `DeferredRegister<AttachmentType<?>>`
                    // takes a `Supplier<? extends I>` whose `I` is the attachment's own type, and
                    // the diamond of `new LinkedHashSet<>()` is nested two inference levels deep
                    // inside it, so it cannot be solved without being told what it is for.
                    () -> AttachmentType.<Set<LivingEntity>>builder(
                            () -> new LinkedHashSet<>()).build());

    /**
     * What was last pushed to this client's HUD, so an unchanged state is not resent. Bandwidth
     * bookkeeping only, not gameplay. An attachment rather than a static map because a map
     * needs a logout cleanup hook and returns null on the first tick, which crashed the server.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SyncState>> LAST_SYNCED =
            ATTACHMENT_TYPES.register("last_synced", () -> AttachmentType.builder(() -> SyncState.NEVER)
                    .serialize(SyncState.CODEC)
                    .build());

    /**
     * The arcabuz: a firestone in the chamber and twenty ticks of fuse.
     *
     * <p><b>No {@code copyOnDeath}, and this is the same rule the guard's points follow.</b>
     * {@code loaded} is a button the player pressed. It rode through the guard's held flag once
     * already -- the player came back from the death screen with the guard already up -- and the
     * arcabuz would have handed them a loaded gun nobody loaded, which is the same category of
     * bug in a different item.</p>
     *
     * <p>So dying costs the fuse <em>and</em> the charge, and both come back on the empty. Which
     * is also the honest reading of the design: a player who just lost the fight did not come
     * back with a loaded weapon.</p>
     *
     * <p>The type witness is not decoration, the same as {@link #LUNGE_HITS}: this
     * {@code DeferredRegister<AttachmentType<?>>} takes a {@code Supplier<? extends I>} and the
     * diamond of {@code ArcabuzState.EMPTY} cannot be solved without being told what it is for.</p>
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ArcabuzState>> ARCABUZ =
            ATTACHMENT_TYPES.register("arcabuz", () -> AttachmentType
                    .<ArcabuzState>builder(() -> ArcabuzState.EMPTY)
                    .serialize(ArcabuzState.CODEC)
                    .build());

    private ModAttachments() {
    }
}
