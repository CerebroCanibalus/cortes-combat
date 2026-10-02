package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own status effects.
 *
 * <p>There is exactly one, and it exists because a number in a field is invisible.</p>
 */
public final class ModEffects {

    private static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, CortesCombat.MODID);

    /**
     * The lunge's invulnerability, as something the player can see.
     *
     * <p><b>The reason this is an effect and not the {@code invulnerableTime} field on its own.</b>
     * The field works: the server re-arms it every tick of the lunge and {@code LivingEntity#hurt}
     * refuses anything while it is positive. What it cannot do is be <em>read</em>. There is no icon
     * for it, no bar, no way to tell from the screen whether the dash is protecting you, and a
     * playtest reported the lunge's invulnerability as "I do not notice it" -- which is a true
     * observation about a mechanic that cannot announce itself.</p>
     *
     * <p>An effect renders in the HUD, so the player can see the window open and count it down. That
     * is the whole reason for it, and it is a legibility problem rather than a rules problem.</p>
     *
     * <p><b>It also duplicates the immunity on purpose</b>, because the field was reported as
     * unreliable -- failing specifically when the player was moving slowly, which is when the lunge
     * runs its full twenty ticks and is therefore at risk from anything that touches the player's
     * damage state during it. Two mechanisms with one intent is not tidiness, but the alternative
     * is a dodge that is sometimes a dodge.</p>
     *
     * <p>{@code NEUTRAL} rather than {@code BENEFICIAL}: it is not a buff the player chose and not
     * one they can keep, and colouring it as a benefit would be claiming something the design does
     * not promise -- that you can stand in it. Its icon is the ropera's, tinted, so it is
     * identifiable rather than a mystery pill.</p>
     */
    public static final DeferredHolder<MobEffect, MobEffect> LUNGE =
            EFFECTS.register("lunge",
                    // The subclass is not decoration: `MobEffect`'s constructors are protected, so
                    // an anonymous body is the only way to build one outside the game.
                    () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xC8B89A) {
                    });

    private ModEffects() {
    }

    /** Hands the register to the mod bus. Called from the mod's constructor. */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        EFFECTS.register(bus);
    }
}