package com.cortes.combat.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The values last pushed to a client, kept only to avoid resending an unchanged HUD state.
 *
 * <p>This started as a {@code static Map} keyed by UUID and immediately caused a crash: the
 * first tick of a joining player looked up a value that was not there yet and unboxed a null.
 * A leak waiting to happen as well. It lives in an attachment now because that is what
 * attachments are for: defined lifecycle, no cleanup handler, no null on the first tick.</p>
 *
 * <p>Not a gameplay value. Nothing reads this to decide damage or cooldown.</p>
 */
public record SyncState(float lastCharge, float lastCooldown, int lastStamina, int lastGuardMax,
                        boolean lastGuardHeld) {

    public static final SyncState NEVER = new SyncState(-1.0F, -1.0F, -1, 0, false);
    public static final Codec<SyncState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("last_charge").forGetter(SyncState::lastCharge),
            Codec.FLOAT.fieldOf("last_cooldown").forGetter(SyncState::lastCooldown),
            Codec.INT.fieldOf("last_stamina").forGetter(SyncState::lastStamina),
            Codec.INT.optionalFieldOf("last_guard_max", 0).forGetter(SyncState::lastGuardMax),
            Codec.BOOL.optionalFieldOf("last_guard_held", false).forGetter(SyncState::lastGuardHeld)
    ).apply(instance, SyncState::new));

    public boolean isDirty(float charge, float cooldown, int stamina, int guardMax,
                            boolean guardHeld, float epsilon) {
        if (lastCharge < 0.0F) {
            return true;
        }
        return Math.abs(charge - lastCharge) >= epsilon
                || Math.abs(cooldown - lastCooldown) >= epsilon
                || stamina != lastStamina
                || guardMax != lastGuardMax
                || guardHeld != lastGuardHeld;
    }
}
