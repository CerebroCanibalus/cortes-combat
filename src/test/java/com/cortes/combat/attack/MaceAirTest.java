package com.cortes.combat.attack;

import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.WeaponProfile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the maza grows a fifth in the air, and that nothing else does.
 *
 * <p>The three numbers are asserted separately even though one factor produces them, because the
 * failure this guards is not "the factor changed" -- it is "the factor was applied to the distance
 * and not to the angles", which is a cone that reaches further without covering more, and that is
 * a different weapon from the one the design asked for.</p>
 */
class MaceAirTest {

    private static final ReachProfile BASE = new ReachProfile(3.4F, 30.0F, 40.0F);

    @Test
    @DisplayName("the maza reaches a fifth further and a fifth wider in the air")
    void theMaceGrowsInTheAir() {
        ReachProfile air = MaceAir.reachFor(WeaponProfile.MACE, BASE, true);

        assertEquals(3.4F * MaceAir.BONUS, air.distance(), 1.0E-5F,
                "**the range**: three point four becomes four point oh eight");
        assertEquals(30.0F * MaceAir.BONUS, air.horizontalAngle(), 1.0E-5F,
                "**the radius**, which is the horizontal angle");
        assertEquals(40.0F * MaceAir.BONUS, air.verticalAngle(), 1.0E-5F,
                "and the vertical one, so the area grows in both directions and not just forward");
    }

    @Test
    @DisplayName("and every other weapon and every other state is untouched")
    void everythingElseIsUnchanged() {
        assertSame(BASE, MaceAir.reachFor(WeaponProfile.MACE, BASE, false),
                "a grounded maza is the base maza");
        assertSame(BASE, MaceAir.reachFor(WeaponProfile.SWORD, BASE, true),
                "**a sword in the air is a sword.** The bonus belongs to the fall, and only one "
                        + "weapon reads the fall");
        assertSame(ReachProfile.NONE, MaceAir.reachFor(WeaponProfile.MACE, ReachProfile.NONE, true),
                "**a NONE cone has to stay none.** Scaling it would invent a small real cone where "
                        + "the weapon has none, which is the bayonet/barrel pair all over again");
    }

    /**
     * The predicate, which is where the water bug lived.
     *
     * <p>"Not on the ground" and "in the air" are the same thing everywhere in this codebase except
     * in water, and the playtest found it: a player paddling about got the fifth-longer reach, the
     * wider cone and the falling meter for free -- "bug total". An elytra is the same shape of
     * wrong, and it is exactly the state vanilla's own {@code canSmashAttack} excludes.</p>
     */
    @Test
    @DisplayName("swimming is not falling, and neither is an elytra or a lava bath")
    void airborneIsNotMerelyOffTheGround() {
        assertTrue(MaceAir.airborne(false, false, false),
                "off the ground, dry, no elytra: this is a fall");
        assertFalse(MaceAir.airborne(true, false, false), "on the ground");
        assertFalse(MaceAir.airborne(false, true, false),
                "**in water.** Not on the ground, and not falling -- the one place in the game "
                        + "where nothing is falling and the maza used to be at its strongest");
        assertFalse(MaceAir.airborne(false, false, true),
                "**and an elytra**, read off vanilla's own canSmashAttack rather than guessed");
        assertFalse(MaceAir.airborne(false, true, true), "both wrong at once is still wrong");
        assertFalse(MaceAir.airborne(true, true, true), "and none of the three alone is enough");
    }
}
