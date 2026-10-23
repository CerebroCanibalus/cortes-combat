package com.cortes.combat.registry;

import com.cortes.combat.CortesCombat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.player.Player;

/**
 * The one damage type this mod declares, and what it is for.
 *
 * <p>Not a {@code DeferredRegister}, because that is for things code creates. A damage type is a
 * JSON file the datapack loads, so the id here and the file under
 * {@code data/cortescombat/damage_type/} are two halves of one declaration and a test checks both.</p>
 *
 * <p><b>The reason it exists: a damage type is the only way to say "this hit ignores armour".</b>
 * The flag has been computed and tested since the warhammer landed; what it could not do is reach
 * the damage calculation. {@code CombatRules.getDamageAfterAbsorb} reads nothing but the source's
 * tags, so no amount of arithmetic before the hit changes what armour takes.</p>
 */
public final class ModDamageTypes {

    /**
     * The warhammer's fully charged swing.
     *
     * <p>Plain {@code resourceKey} and not a {@code ResourceKey.create} at the use site, because a
     * misspelled id is a {@code MissingRegistryKeyException} thrown while the player is mid-swing.</p>
     */
    public static final ResourceKey<DamageType> WARHAMMER_IGNORED_ARMOUR = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            CortesCombat.rl("warhammer_ignored_armour"));

    /**
     * The axe's fully charged swing.
     *
     * <p><b>It is not in {@code bypasses_armor}, and that is the difference from the warhammer's
     * one.</b> The warhammer reaches past armour; the axe does not, it just takes the armour apart
     * twice as fast. Tagging this one the same way would have made a charged axe hit for full
     * damage through a diamond plate, which nobody asked for and which would have made the weapon
     * strictly better than the warhammer at the one thing the warhammer is for.</p>
     *
     * <p>So this type exists to be <em>recognised</em>, not to behave differently: the armour wear
     * handler looks for it, and that is all.</p>
     */
    public static final ResourceKey<DamageType> AXE_CHARGED = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            CortesCombat.rl("axe_charged"));

    private ModDamageTypes() {
    }

    /**
     * A source that still counts as the player's own hit, in every way except armour.
     *
     * <p>The player is passed as both the causing and the direct entity, exactly the way
     * {@code DamageSources#playerAttack} does it, because everything vanilla hangs off a player
     * attack -- the kill credit, the enchantments that fire on one, the death message -- is read
     * from those two and not from the type.</p>
     */
    public static DamageSource warhammerIgnoredArmour(Player player) {
        return player.damageSources().source(WARHAMMER_IGNORED_ARMOUR, player);
    }

    /** The same source for the axe's charged swing, which keeps its armour and loses it twice over. */
    public static DamageSource axeCharged(Player player) {
        return player.damageSources().source(AXE_CHARGED, player);
    }

    /**
     * Whether this hit is the axe's charged one.
     *
     * <p>Read off the source's type rather than carried on the result, because the two consumers
     * live on different threads of the game: the damage already left the attack by the time the
     * armour event fires, and the only thing they still share is the source.</p>
     */
    public static boolean isAxeCharged(DamageSource source) {
        return source != null && source.is(AXE_CHARGED);
    }
}