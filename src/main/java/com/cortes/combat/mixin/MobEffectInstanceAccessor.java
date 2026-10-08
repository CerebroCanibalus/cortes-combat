package com.cortes.combat.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The one field {@code MobEffectInstance} has no public setter for.
 *
 * <p><b>An accessor and not the deny-and-re-add dance, and the reason is a bug that hung the
 * server.</b> The first version of the antiseptic refused the effect with
 * {@code MobEffectEvent.Applicable} and re-added a shorter one through
 * {@code LivingEntity#forceAddEffect}. That looked clean until the runtime was read:
 * {@code forceAddEffect} <em>also</em> calls {@code CommonHooks.canMobEffectBeApplied}, so it
 * posts {@code Applicable} again, which reached the same handler, which called
 * {@code forceAddEffect} again -- an unbounded recursion reported as "parrying lags the whole
 * server".</p>
 *
 * <p>What makes this the right answer instead: {@code Applicable} fires with the very instance
 * that is about to be stored, so mutating its duration there is the whole fix -- no second
 * application, no re-entry, no flag. The only missing piece was a way to write the field.</p>
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {

    /** Sets the remaining ticks. The name is prefixed so it cannot collide with a future vanilla one. */
    @Accessor("duration")
    void cortescombat$setDuration(int duration);
}
