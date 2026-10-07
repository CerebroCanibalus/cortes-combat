package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.weapon.MaceFall;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * That a fall is worth something to one weapon and nothing to the rest.
 *
 * <h2>Why this is separate from {@link MaceFallTest}</h2>
 *
 * <p>Because {@link MaceFall} is a curve nobody calls and this is the call. A tested function with
 * no caller is a comment that costs a sum &mdash; the exact shape this project has paid for, and
 * the reason the numbers are asserted through the same door the game goes through.</p>
 */
class MaceFallDamageTest {

    /** Enough charge data that the maza's is NONE, which is what the design says it is. */
    private static final ChargeProfile NO_CHARGE = ChargeProfile.NONE;

    private static float damageOf(WeaponProfile profile, float fall, float baseDamage) {
        return DamageResolver.resolve(baseDamage, NO_CHARGE, profile, 0.0F, null, fall).damage();
    }

    /**
     * The fall reaches the damage, and only for the maza.
     *
     * <p>Both directions, because either alone is a hole: "the maza's fall does nothing" and "a
     * sword's fall does something" are the two ways this can rot, and a test of the first is silent
     * about the second.</p>
     */
    @Test
    @DisplayName("a fall is worth the whole weapon to the maza and nothing to anyone else")
    void theFallOnlyPaysTheMace() {
        float onTheGround = damageOf(WeaponProfile.MACE, 0.0F, MaceFall.BASE_DAMAGE);
        float fromTheCap = damageOf(WeaponProfile.MACE, MaceFall.CAP_FALL, MaceFall.BASE_DAMAGE);
        assertEquals(MaceFall.MAX_DAMAGE, fromTheCap, 0.0001F,
                "**the cap is the whole weapon**: fifteen on the ground, fifty at thirty one "
                        + "blocks. If this is unchanged the curve is not wired in at all, and every "
                        + "number in MaceFall is a comment that costs a sum");
        assertEquals(MaceFall.BASE_DAMAGE, onTheGround, 0.0001F);

        for (WeaponProfile profile : WeaponProfile.values()) {
            if (profile == WeaponProfile.MACE) {
                continue;
            }
            assertEquals(damageOf(profile, 0.0F, 7.0F), damageOf(profile, 400.0F, 7.0F),
                    0.0001F,
                    profile + " gains damage from falling. **Only the maza reads a fall** -- "
                            + "four hundred blocks is worth nothing to anything else, and a "
                            + "sword that pays for height is a different game");
        }
    }

    /**
     * Added, not multiplied.
     *
     * <p><b>The whole reason this is a separate test.</b> The two read as the same sentence about
     * commitment -- "how long did this take" -- so composing them is the obvious mistake and the
     * one that would survive: a twelve block fall would be worth twice a six block one instead of
     * one and a third, and the cap would still hold, so every other assertion here would pass.</p>
     *
     * <p>Twelve blocks is not in the curve's linear part, so the test uses two heights inside it
     * and asks whether the second is <em>more</em> than the first and less than twice.</p>
     */
    @Test
    @DisplayName("a bigger fall is worth more, and not more than proportionally")
    void theFallIsAddedNotMultiplied() {
        float atThree = damageOf(WeaponProfile.MACE, 3.0F, MaceFall.BASE_DAMAGE);
        float atSix = damageOf(WeaponProfile.MACE, 6.0F, MaceFall.BASE_DAMAGE);

        assertEquals(MaceFall.BASE_DAMAGE + 12.0F * MaceFall.SCALE, atThree, 0.0001F);
        assertEquals(MaceFall.BASE_DAMAGE + 18.0F * MaceFall.SCALE, atSix, 0.0001F);
        assertEquals(true, atSix > atThree, "twice the fall is worth more");
        assertEquals(true, atSix - atThree < atThree - MaceFall.BASE_DAMAGE,
                "**and worth less than twice as much.** A multiplied fall makes the second six "
                        + "blocks worth two firsts, which is a curve that only bends if you know it "
                        + "is there -- and the cap would not notice");
    }

    /**
     * The maza's charge is the other half of this.
     *
     * <p>Asserted through the same call rather than through the table, because the table already
     * has its own test and this one is about what happens when the two meet: a weapon that cannot
     * be charged cannot have its charge and its fall multiplied together.</p>
     */
    @Test
    @DisplayName("a charge ratio cannot buy the maza anything, because it has no charge")
    @SuppressWarnings("unused")
    void theMaceHasNoCharge() {
        float withRatio = DamageResolver.resolve(
                MaceFall.BASE_DAMAGE, NO_CHARGE, WeaponProfile.MACE, 1.0F, null,
                MaceFall.CAP_FALL).damage();
        assertEquals(MaceFall.MAX_DAMAGE, withRatio, 0.0001F,
                "**a full charge on the maza is the same as a tap**, and it is still fifty. "
                        + "The weapon has one commitment and it is the height");
    }
}
