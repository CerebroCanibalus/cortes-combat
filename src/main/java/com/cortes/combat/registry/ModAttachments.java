package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import com.cortes.combat.state.ChargeState;
import com.cortes.combat.state.CooldownState;
import com.cortes.combat.state.GuardState;
import com.cortes.combat.state.ParryState;
import com.cortes.combat.state.StreakState;
import com.cortes.combat.state.SyncState;
import net.neoforged.neoforge.attachment.AttachmentType;
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
     * What was last pushed to this client's HUD, so an unchanged state is not resent. Bandwidth
     * bookkeeping only, not gameplay. An attachment rather than a static map because a map
     * needs a logout cleanup hook and returns null on the first tick, which crashed the server.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SyncState>> LAST_SYNCED =
            ATTACHMENT_TYPES.register("last_synced", () -> AttachmentType.builder(() -> SyncState.NEVER)
                    .serialize(SyncState.CODEC)
                    .build());

    private ModAttachments() {
    }
}
