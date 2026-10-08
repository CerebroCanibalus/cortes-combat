package com.cortes.combat.attack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponTables;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a strike which can reach several things says so, and reaches them in the right order.
 *
 * <h2>Why "one" is a default and not a row</h2>
 *
 * <p>Because a weapon nobody decided about should swing at one thing. The two area weapons are
 * additions to that, so adding a profile to the enum produces a single-target weapon with no
 * decision required &mdash; and the test that says so is the one below, which asserts every
 * profile rather than a hand-written list of the two.</p>
 */
class AreaStrikeTest {

    /** The whole point of the enum's last value: nobody had to say it hits one thing. */
    @Test
    @DisplayName("a strike reaches one thing unless the tables say otherwise")
    void oneIsTheDefault() {
        assertEquals(1, WeaponTables.maxTargets(WeaponProfile.SWORD),
                "the sword is what every other weapon is measured against and it hits one thing");
        assertEquals(1, WeaponTables.maxTargets(WeaponProfile.UNARMED));
        assertEquals(1, WeaponTables.maxTargets(WeaponProfile.PICKAXE),
                "and a pickaxe, which does not fight at all, is still a single-target answer");

        assertEquals(3, WeaponTables.maxTargets(WeaponProfile.MACE),
                "**the maza is three.** Its body and its weight are the argument: it clears what is "
                        + "in front of it and nothing past the swing");
        assertEquals(6, WeaponTables.maxTargets(WeaponProfile.HOE),
                "**and the hoe is six.** Four times the maza's, at a third of the knockback and "
                        + "a quarter of the damage, which is the difference between a shove and a "
                        + "sweep and the reason they are not the same weapon twice");

        for (WeaponProfile profile : WeaponProfile.values()) {
            assertTrue(WeaponTables.maxTargets(profile) >= 1,
                    profile + " can hit " + WeaponTables.maxTargets(profile) + " things. Zero would "
                            + "make a weapon that cannot connect at all, which is not a state "
                            + "anything in this project wants");
        }
    }

    /** The one question, and whether it is the same one for both. */
    @Test
    @DisplayName("hitting several is asked, not inferred from a number")
    void hittingSeveralIsAsked() {
        assertTrue(WeaponTables.hitsSeveral(WeaponProfile.MACE));
        assertTrue(WeaponTables.hitsSeveral(WeaponProfile.HOE));
        assertFalse(WeaponTables.hitsSeveral(WeaponProfile.SWORD),
                "the sword asks the same question rather than reading its own count, so a weapon "
                        + "that reaches two things for some other reason does not silently become "
                        + "an area weapon");
    }

    /**
     * The aimed mob pays first, and the rest come after.
     *
     * <p><b>Read from the source, because the bug this exists for is invisible in a build.</b>
     * The wrong order is not a crash and not a wrong number: a player aiming at the mob behind a
     * crowd watches the mob in front take the hit instead, and the report is "it hit the wrong
     * one" &mdash; the same sentence the cone and reach bugs produced twice before.</p>
     *
     * <p>So the assertion is positional: the aimed target is applied, and only then is the list of
     * extras walked. It does not check that the list is sorted, because that is
     * {@code ConeTargets}' own job and it has a test of its own.</p>
     */
    @Test
    @DisplayName("the aimed target is paid before the extras are collected")
    void theAimedOnePaysFirst() {
        String engine = read();

        int aimed = engine.indexOf("apply(player, target, stats, result, chargeRatio, smash);");
        int extras = engine.indexOf("ConeTargets.nearestInCone(");
        assertTrue(aimed > 0 && extras > 0,
                "**both halves have to be there**: one apply for the target the client named, and "
                        + "one scan for the rest. Either alone is a single-target weapon or an "
                        + "area weapon that ignores what the player was aiming at");
        assertTrue(aimed < extras,
                "**and the aimed one has to come first.** A sweep running first would put the hit "
                        + "on whatever happened to be nearest, which is the mob in front of the one "
                        + "the player chose. The client sent an id precisely because the server "
                        + "cannot reconstruct intent; spending it on the second mob is throwing "
                        + "that away");

        assertTrue(engine.contains("if (other == target)") || engine.contains("other != target"),
                "**and the aimed mob must not be paid twice.** The nearest-first list includes it, "
                        + "so without this a single-target swing against a crowd does double "
                        + "damage -- a bug with no report at all, since it looks like a buff");
    }

    /**
     * The extras are in the weapon's own cone.
     *
     * <p>The halberd's sweep deliberately reads a wider and longer reach, because it costs four
     * swings. A weapon that is also the strongest in the set and hits three things on one swing
     * does not get that on top: the reach is the weapon's.</p>
     */
    @Test
    @DisplayName("the extras use the weapon's own reach, not the sweep's")
    void theExtrasUseTheWeaponsCone() {
        String engine = read();

        assertTrue(engine.contains("stats.reach()"),
                "**the scan has to use `stats.reach()`.** Passing the halberd's derived reach here "
                        + "would give the maza a longer, wider cone than its own left click while "
                        + "paying one swing for it, which is the halberd's whole bargain given "
                        + "away for free");
        assertFalse(engine.contains("HalberdSweep.reachFor"),
                "and specifically not the sweep's: a swing that costs one is not a sweep that costs "
                        + "four");
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
