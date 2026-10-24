package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a landed hit actually costs the weapon something.
 *
 * <h2>The hole this found</h2>
 *
 * <p>The playtest: "de momento ningún arma se gasta realmente". Correct, and the reason is the same
 * shape as every other bug this project has paid for: <b>vanilla's wear lives on the item, and the
 * item is the one thing this mod never calls.</b> {@code Item.postHurtEnemy} is what runs
 * {@code stack.hurtAndBreak(1, attacker, MAINHAND)}, it is invoked from {@code Item.hurtEnemy}, and
 * that whole path was replaced by {@code CombatEngine} -- so thirty weapons and every vanilla tool
 * that came back into the fight have been free to swing forever, with a durability bar that never
 * moved and a tooltip number that was decoration.</p>
 *
 * <p>It is invisible from every direction: the item has a durability, the bar renders, the number
 * is right, and nothing ever decreases it. A test of the number passes; a test of the bar passes;
 * the only failing assertion is "swing a hundred times and count".</p>
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>Because the call is one line in a server-side method that needs a level, a player and a target
 * to reach, and the rule it encodes -- <em>one point per landed hit, for every weapon</em> -- is
 * the only thing worth asserting. Brittle by design; the alternative is a hundred swings.</p>
 */
class WeaponWearTest {

    @Test
    @DisplayName("a landed hit wears the weapon, the way vanilla's postHurtEnemy did")
    void aLandedHitWearsTheWeapon() {
        String engine = read();

        assertTrue(engine.contains("ItemDamage.damageMainHand(player, 1)"),
                "**`CombatEngine` tiene que desgastar el arma en cada golpe que aterriza.** "
                        + "Vanilla lo hace en `Item.postHurtEnemy`, que vive en el item, y este "
                        + "mod reemplaza el flujo de ataque entero -- asi que ese camino nunca "
                        + "corre y ninguna arma del juego pierde un punto de durabilidad. El "
                        + "sintoma es una barra que se dibuja, un numero que se lee y nada que "
                        + "baje: se ve bien desde todos los lados menos desde 'pega cien veces y "
                        + "contá'");

        assertTrue(readItemDamage().contains("EquipmentSlot.MAINHAND"),
                "y sobre la mano principal, que es lo que vanilla pasa: la durabilidad del arma "
                        + "que se esta usando, no la de otra cosa");
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

    /**
     * The payment moved to the seam: the slot is spelled there now, one spelling per leg.
     * The rule ("the hand being used") is asserted where the slot is named, not where the
     * point is paid.
     */
    private static String readItemDamage() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "platform",
                "ItemDamage.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
    }
}
