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

    /**
     * Copper armour's antiseptic, one level per piece worn.
     *
     * <p>Applied and refreshed by {@code CopperEffects} from the count of copper pieces, with the
     * amplifier equal to the count minus one -- so one piece is level I and all four are level IV,
     * which is the twenty percent the design asked for at the top end. The effect itself carries no
     * behaviour: the <em>handler</em> reads its level when a debuff lands, because the reduction
     * has to happen at the moment of application and the effect is just the number that says how
     * much.</p>
     *
     * <p>Icon: {@code textures/mob_effect/antiseptic.png}. The asset was drawn by the user and is
     * copied here from {@code textures/effects/} under the effect's own id -- {@code MobEffect}
     * resolves the icon at {@code assets/<ns>/textures/mob_effect/<path>.png}, so a file in any
     * other folder is a file the HUD never looks at. The colour is copper's own, so a missing icon
     * would at least still read as ours.</p>
     */
    public static final DeferredHolder<MobEffect, MobEffect> ANTISEPTIC =
            EFFECTS.register("antiseptic",
                    () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xD07A4A) {
                    });

    /**
     * The copper weapon's purification: fast pure damage to the undead and to netherite wearers,
     * and a thirty percent cut to the damage its victim deals.
     *
     * <p>A real subclass rather than an anonymous body, because unlike the other two this one does
     * something every tick. See {@link com.cortes.combat.effect.PurificationEffect}.</p>
     */
    public static final DeferredHolder<MobEffect, MobEffect> PURIFICATION =
            EFFECTS.register("purification", com.cortes.combat.effect.PurificationEffect::new);

    /**
     * Revelacion: what the running reactor leaves on everything hostile inside its radius.
     *
     * <p>A real subclass, because unlike the two above it kills -- see
     * {@link com.cortes.combat.effect.RevelationEffect}. Its icon lives at
     * {@code textures/mob_effect/revelacion.png}, copied from {@code textures/effects/} under the
     * effect's own id for the same reason antiseptic's does: that folder is the only one the HUD
     * reads.</p>
     */
    public static final DeferredHolder<MobEffect, MobEffect> REVELATION =
            EFFECTS.register("revelacion", com.cortes.combat.effect.RevelationEffect::new);

    /** Anti-curacion: lo que deja Cicatrizador. El trabajo vive en su handler. */
    public static final DeferredHolder<MobEffect, MobEffect> ANTI_HEALING =
            EFFECTS.register("anti_healing", com.cortes.combat.effect.AntiHealingEffect::new);

    /** Sangrado: lo que deja un cargado de alabarda. */
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING =
            EFFECTS.register("bleeding", com.cortes.combat.effect.BleedingEffect::new);

    /** Quebranto: la armadura del rival vale menos. */
    public static final DeferredHolder<MobEffect, MobEffect> QUEBRANTO =
            EFFECTS.register("quebranto", com.cortes.combat.effect.QuebrantoEffect::new);

    /** Desvio Vampirico: la vida maxima que el jugador se quedo. */
    public static final DeferredHolder<MobEffect, MobEffect> VAMPIRIC =
            EFFECTS.register("vampiric", com.cortes.combat.effect.VampiricEffect::new);

    /** Y el techo que le falta al que fue desviado. Dos efectos y no uno: ver DrainedEffect. */
    public static final DeferredHolder<MobEffect, MobEffect> DRAINED =
            EFFECTS.register("drained", com.cortes.combat.effect.DrainedEffect::new);

    private ModEffects() {
    }

    /** Hands the register to the mod bus. Called from the mod's constructor. */
    public static void register(net.neoforged.bus.api.IEventBus bus) {
        EFFECTS.register(bus);
    }
}