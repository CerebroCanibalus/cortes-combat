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
     * Sweeping Edge ya no es un bonus de daño: es una onda que vuela.
     *
     * <p>El test cambia con el encantamiento y conserva lo que lo hacía útil: <b>una fórmula pura
     * sin llamador es el bug que esta clase sigue encontrando</b>. La tabla de niveles puede estar
     * perfecta y el encantamiento no hacer nada si nadie dispara la onda -- así que la aserción es
     * que el motor la dispara, y que la dispara salteando al objetivo que el melé ya cobró.</p>
     */
    @Test
    @DisplayName("Sweeping Edge dispara la onda, y la onda saltea al objetivo del golpe")
    void sweepingEdgeFiresTheWave() {
        String engine = read();

        assertTrue(engine.contains("SlashWave.fire("),
                "**la onda tiene que tener llamador.** Una fórmula pura sin nadie que la dispare es "
                        + "el bug que esta clase sigue encontrando: la tabla quedaría bien y el "
                        + "encantamiento no haría nada");
        assertTrue(engine.contains("fireSweepingWave("),
                "y a través del helper del motor, donde el nivel, la carga y el daño resuelto se "
                        + "encuentran");
        assertTrue(engine.contains("aimed.getId()"),
                "**y saltea al objetivo del melé**: la onda es el seguimiento del tajo más allá del "
                        + "cuerpo, no un segundo cobro al mismo");

        // El cono del golpe sigue contando lo que siempre contó: la onda es un proyectil aparte y
        // no le suma objetivos a la espada.
        assertTrue(engine.contains("WeaponTables.maxTargets(stats.profile())"),
                "el número de cosas que pega UN golpe sigue siendo `maxTargets`; la onda no le "
                        + "agrega objetivos al cono");
        assertFalse(engine.contains("maxTargets(profile, ") || engine.contains("maxTargets(profile,"),
                "y la tabla no toma un encantamiento: el conteo es un hecho del arma, no del item's "
                        + "enchantments");
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
