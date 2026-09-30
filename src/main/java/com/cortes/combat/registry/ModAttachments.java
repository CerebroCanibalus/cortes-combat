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

    /** Remaining guard hits. The maximum comes from the item, not from here. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GuardState>> GUARD_STAMINA =
            ATTACHMENT_TYPES.register("guard_stamina", () -> AttachmentType.builder(() -> GuardState.EMPTY)
                    .serialize(GuardState.CODEC)
                    .copyOnDeath()
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
