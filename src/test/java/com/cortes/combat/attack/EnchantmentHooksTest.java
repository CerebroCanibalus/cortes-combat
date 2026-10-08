package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the three enchantment hooks a replaced attack flow drops are actually called, and that the
 * crowd charge stays single-target.
 *
 * <h2>The hole this exists for, which this project has paid for four times</h2>
 *
 * <p><b>A tested function with no caller, and a caller with no test.</b> The maza's fall, the
 * sword's subtraction, the wear-on-hit and the trident's pull all shipped that way: the arithmetic
 * was verified by hand, the engine computed its own copy or none at all, and the build was green.
 * The enchantment hooks are the same shape one level up: {@code EnchantmentHelper} is a library of
 * correct functions nobody in this mod calls, because {@code Player#attack} was replaced whole.
 * Reading the source is brittle by design, and the alternative is a playtest with an enchanted
 * sword and no way to tell "Smite did nothing" from "the mob was not undead".</p>
 */
class EnchantmentHooksTest {

    @Test
    @DisplayName("the damage, knockback and post-attack hooks are all called")
    void theThreeHooksAreCalled() {
        String engine = read();

        assertTrue(engine.contains("EnchantmentHelper.modifyDamage("),
                "**Sharpness, Smite, Bane of Arthropods, Impaling and Density run here.** They are "
                        + "the body of `ServerPlayer#getEnchantedDamage`, which only `Player#attack` "
                        + "calls -- and that flow is replaced. Without this line an enchanted sword "
                        + "does exactly the damage of an unenchanted one, silently");

        assertTrue(engine.contains("EnchantmentHelper.modifyKnockback("),
                "**and Knockback.** Vanilla's `getKnockback` is the other half of the same lost "
                        + "call; without it a Knockback II sword shoves like a plain one");

        assertTrue(engine.contains("EnchantmentHelper.doPostAttackEffects("),
                "**and the post-attack effects.** This is where Aspect of Fire and Wind Burst "
                        + "live -- an enchanted sword that cannot set anything on fire is a bug a "
                        + "player notices, a build a test does not");
    }

    @Test
    @DisplayName("the post-attack effects run once per body that took the hit")
    void oncePerLandedBody() {
        String engine = read();
        int guard = engine.indexOf("if (anyLanded)");
        int effects = engine.indexOf("EnchantmentHelper.doPostAttackEffects(");

        assertTrue(guard > 0 && effects > 0);
        assertTrue(guard < effects,
                "**inside the landed branch.** Firing it for a swing that hit nothing would set "
                        + "the attacker's hands' enchantments loose on a whiff, and the guard is "
                        + "what says the hook is about a hit");
    }

    /**
     * The crowd charge is a damage bonus, not a second area weapon.
     *
     * <p>The reworked Sweeping Edge is deliberate that way: the design already has the halberd and
     * the hoe for area, and a third source of "hits everything in the arc" is the thing it was
     * retuned away from. So the assertion is paired: the bonus feeds the damage, and the target
     * count still comes from the one table that ever decided it.</p>
     */
    @Test
    @DisplayName("Sweeping Edge adds damage but never targets")
    void theCrowdChargeStaysSingleTarget() {
        String engine = read();

        assertTrue(engine.contains("SweepingEdge.bonus("),
                "**the crowd charge has to reach the damage.** A pure formula with no caller is "
                        + "the bug this class keeps finding: the table would be right and the "
                        + "enchantment would do nothing");
        assertTrue(engine.contains("sweepingEdgeBonus("),
                "and through the engine's own helper, so the level and the count meet here");

        // The target count is still the weapon's own row, untouched by the enchantment: the extra
        // bodies are still collected from the area cap and nowhere else.
        assertTrue(engine.contains("WeaponTables.maxTargets(stats.profile())"),
                "**the number of things one swing hits is still `maxTargets`.** If Sweeping Edge "
                        + "ever started adding targets it would appear here, next to this line, "
                        + "and a second area weapon nobody decided would be born");

        assertFalse(engine.contains("maxTargets(profile, ") || engine.contains("maxTargets(profile,"),
                "and the table takes no enchantment argument: the count is a fact about the "
                        + "weapon, not about the item's enchantments");
    }

    /**
     * NeoForge's attack hook is fired again, for the target the player aimed at.
     *
     * <p>{@code Player#attack} opens with {@code CommonHooks.onPlayerAttackTarget}, and a replaced
     * flow that skips it is invisible until a protection mod that cancels an attack stops working.
     * Fired for the aimed target only, the way vanilla fires it once per attack rather than once
     * per sweep victim.</p>
     */
    @Test
    @DisplayName("NeoForge's attack hook fires again")
    void theCompatHookIsBack() {
        assertTrue(read().contains("CommonHooks.onPlayerAttackTarget("),
                "**`Player#attack` fires this and the replaced flow dropped it.** It is how another "
                        + "mod cancels or reacts to a player hitting something, and losing it is a "
                        + "compatibility break that no number can show");
    }

    private static String read() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "CombatEngine.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
