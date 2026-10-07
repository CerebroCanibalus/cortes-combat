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
 * That the trident's two halves are opposites, and that only the charged one is.
 *
 * <h2>The design in one sentence</h2>
 *
 * <p>Its reach is the longest in the game and its charged thrust <b>drags the target in</b>. Those
 * two facts are one weapon: five and a half blocks of corridor is a reach the target leaves before
 * the player can use it again, and pulling brings the second cut back inside. Break either and the
 * other stops making sense &mdash; a trident that reaches far and pushes away spends its range on
 * a gap it just made.</p>
 */
class TridentPullTest {

    /** One weapon drags, and the drag is one and a third blocks. */
    @Test
    @DisplayName("one weapon pulls, and it pulls one and a third blocks")
    void onlyTheTridentPulls() {
        assertEquals(1.3F, WeaponTables.pullFor(WeaponProfile.TRIDENT), 1.0E-6F,
                "the number the design settled on");

        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile != WeaponProfile.TRIDENT) {
                assertEquals(0.0F, WeaponTables.pullFor(profile), 1.0E-6F,
                        profile + " drags its target in. The pull is the trident's whole argument "
                                + "and a weapon that also had it would have two ways of being close "
                                + "and neither would be special");
            }
        }
    }

    /**
     * The pull is shorter than the reach it is spent on.
     *
     * <p><b>This is the arithmetic the weapon lives or dies on, and it is asserted as a
     * relationship so a retune cannot quietly break it.</b> The combo is: thrust at five and a
     * half, drag one and a third, and the second cut lands at four and a sixth &mdash; inside five
     * and a half. If the pull were as long as the reach, the second cut would be a tap at arm's
     * length; if it were longer, the target would arrive before the player committed to the
     * second one.</p>
     */
    @Test
    @DisplayName("the drag brings the target back inside the reach, and not past the player")
    void theDragBringsItBackIntoReach() {
        float reach = WeaponTables.reach(WeaponProfile.TRIDENT).distance();
        float pull = WeaponTables.pullFor(WeaponProfile.TRIDENT);

        assertTrue(pull < reach * 0.5F,
                "**the drag has to be under half the reach.** At five and a half and one and a "
                        + "third it is under a quarter, which leaves the second cut four blocks "
                        + "out and still aimed. A drag longer than half the reach turns the "
                        + "second swing into a point-blank one, and the weapon stops being about "
                        + "range");
        assertTrue(reach - pull > 0.0F,
                "and it must not overshoot into the player: five and a half less one and a third "
                        + "is four and a sixth, and a target delivered onto your own position is "
                        + "not a combo");
    }

    /**
     * It is the charged half, read from the same threshold the damage reads.
     *
     * <p>A tap has to shove like everything else. If a tap dragged too, the trident would have two
     * behaviours and no way to ask for one of them, and the charged thrust would be the same swing
     * as the tap with more commitment attached &mdash; which is a weapon nobody aims.</p>
     */
    @Test
    @DisplayName("the tap shoves and the charge drags, and the gate is the damage's own")
    void onlyTheChargePulls() {
        String engine = read();

        assertTrue(engine.contains("chargeRatio >= DamageResolver.FULL_CHARGE"),
                "**the gate has to be the damage's own threshold, and it has to be a comparison and "
                        + "not a truthy check.** `DamageResolver.FULL_CHARGE` is the number the "
                        + "damage already decided, so asking it here means the half of the swing "
                        + "that hurt and the half that moved can never disagree");
        assertTrue(engine.contains("Knockback.pull(") && engine.contains("Knockback.apply("),
                "**and both paths have to exist.** An `else` that was forgotten would leave the "
                        + "trident shoving on a full charge, which is the ordinary behaviour and "
                        + "therefore the bug nobody reports");
    }

    /**
     * The push clamps to zero, which is why the pull cannot be a negative knockback.
     *
     * <p>Asserted because it is the whole argument for a separate field: someone reading the
     * table later will try to write {@code -1.3} here, and that lands on zero without an error.</p>
     */
    @Test
    @DisplayName("a negative knockback would be clamped to nothing")
    void aNegativeWouldVanish() {
        var negative = new com.cortes.combat.core.weapon.KnockbackProfile(-1.3F, 0.0F);
        assertEquals(0.0F, negative.clampedHorizontal(), 1.0E-6F,
                "**a negative horizontal clamps to zero, silently.** That is the bound that stops "
                        + "a config override pulling anybody through the floor, and it is why the "
                        + "trident's drag is its own number with its own method rather than a "
                        + "minus sign in the table");

        var positive = new com.cortes.combat.core.weapon.KnockbackProfile(1.05F, 0.12F);
        assertEquals(1.05F, positive.clampedHorizontal(), 1.0E-6F,
                "and the shovel at one and five hundredths is inside the raised ceiling, so the "
                        + "biggest shove in the game is a real number rather than a clamped one");
    }

    /**
     * The sword loses one point, and only to its own modifier.
     *
     * <p>Vanilla's six materials all read {@code 3 + tier bonus}: four, five, six, seven and
     * eight, plus gold at four. One off each is three, four, five, six and seven. <b>The test
     * asserts the arithmetic and not a table</b>, because the code subtracts from whatever the item
     * says &mdash; which is the whole reason it cannot drift when a mod ships an eighth sword.</p>
     */
    @Test
    @DisplayName("a sword loses one point from its own number, floored at zero")
    void aSwordLosesOne() {
        // Vanilla's six, in the order Tiers declares them.
        float[] vanillaOffsets = {0.0F, 1.0F, 2.0F, 3.0F, 0.0F, 4.0F};
        float[] expected = {2.0F, 3.0F, 4.0F, 5.0F, 2.0F, 6.0F};
        for (int i = 0; i < vanillaOffsets.length; i++) {
            float swordOffset = 3.0F + vanillaOffsets[i];
            assertEquals(expected[i], SwordRebalance.reduced(swordOffset), 1.0E-6F,
                    "the sword whose offset is " + swordOffset + " ends at " + expected[i]);
        }
        assertEquals(0.0F, SwordRebalance.reduced(0.5F), 1.0E-6F,
                "**and it floors at zero rather than going negative.** A mod's sword may declare "
                        + "less than one point of damage, and a negative attack damage is a weapon "
                        + "that heals with a swing animation");
    }

    /**
     * The event calls the tested function.
     *
     * <p><b>The same hole as the maza's fall, fourth time.</b> {@link SwordRebalance#reduced} was
     * tested and the event computed the same delta separately. Removing the subtraction from the
     * event left the whole suite green: <b>a tested method with no caller, and a caller with no
     * test</b>, agreeing by coincidence.</p>
     */
    @Test
    @DisplayName("the event goes through the function the test walks, not through its own copy")
    void theEventUsesTheTestedArithmetic() {
        String event = readEvent();
        assertTrue(event.contains("reducedOf(entry)") || event.contains("reduced(entry"),
                "**the event has to call the tested arithmetic.** It had its own copy, and the two "
                        + "agreed because both said minus one -- not because anything connected "
                        + "them. Verified by sabotage: deleting the subtraction from the event "
                        + "left every test in the project green while the swords kept their old "
                        + "damage");
    }

    private static String readEvent() {
        Path file = Path.of("src", "main", "java", "com", "cortes", "combat", "attack",
                "SwordRebalance.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + file + ": " + e.getMessage(), e);
        }
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
