package com.cortes.combat.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the maza's fall is worth what it says, and stops where it says.
 *
 * <h2>Why the cap is the whole point of this file</h2>
 *
 * <p>Vanilla's last segment is {@code 22 + fall - 8}. It is not capped, so forty blocks is about
 * fifty-four bonus damage and a player who finds a suitable tower can trivially exceed every number
 * in the game. The design says the maza's ceiling is {@link MaceFall#MAX_DAMAGE}.</p>
 *
 * <p><b>And the cap is asserted against the whole curve, not at one point.</b> A cap tested only at
 * forty blocks passes on a curve that is uncapped at ten and clamped from twelve, which is the shape
 * a {@code min()} bolted onto the wrong segment produces.</p>
 */
class MaceFallTest {

    /** The four numbers the design fixes, asserted first so a later failure has a place to point. */
    @Test
    @DisplayName("the ceiling is fifty, and it is the base plus the bonus")
    void theCeiling() {
        assertEquals(15.0F, MaceFall.BASE_DAMAGE,
                "base damage. **It was ten and the playtest moved it up five**, together with the "
                        + "ceiling: the maza is the one weapon whose whole argument is height, and "
                        + "a base that low made it a worse club on the ground");
        assertEquals(35.0F, MaceFall.BONUS_CEILING, "what the fall is worth");
        assertEquals(50.0F, MaceFall.MAX_DAMAGE, "**the cap is the base plus the bonus, 50**");
    }

    /**
     * Nothing without a fall.
     *
     * <p>And this is not a formality: <b>a maza standing on the floor has to be a club and not a
     * one-shot</b>. If the threshold moved to zero the weapon would be strictly better for every
     * player who never falls, and the fall would stop being the thing it is.</p>
     */
    @Test
    @DisplayName("without a fall it is a club")
    void noFallNoBonus() {
        assertEquals(0.0F, MaceFall.bonusFor(0.0F));
        assertEquals(0.0F, MaceFall.bonusFor(MaceFall.THRESHOLD), "the threshold itself pays nothing");
        assertEquals(0.0F, MaceFall.bonusFor(-3.0F), "and a negative fall cannot pay either");
        assertEquals(MaceFall.BASE_DAMAGE, MaceFall.damageFor(0.0F),
                "which is what a grounded maza does: ten");
    }

    /**
     * The cap holds at every distance, which is the assertion the design turns on.
     *
     * <p>Swept rather than sampled at a few points, because a cap can be wrong in a narrow band
     * and still be right at every number somebody thought to write down.</p>
     */
    @Test
    @DisplayName("the cap holds at every distance, not just the big ones")
    void theCapHoldsEverywhere() {
        float worstAbove = 0.0F;
        float atWhat = 0.0F;
        for (float fall = 0.0F; fall <= 400.0F; fall += 0.25F) {
            float bonus = MaceFall.bonusFor(fall);
            assertTrue(bonus >= 0.0F, "a bonus cannot be negative, and it was " + bonus);
            assertTrue(bonus <= MaceFall.BONUS_CEILING + 0.0001F,
                    "the bonus passed the cap at " + fall + " blocks: " + bonus);
            if (bonus > worstAbove) {
                worstAbove = bonus;
                atWhat = fall;
            }
        }
        assertEquals(MaceFall.BONUS_CEILING, worstAbove, 0.0001F,
                "and it actually reaches the cap, at " + atWhat
                        + " blocks. A curve that merely stays under the cap is a curve where the "
                        + "ceiling is unreachable, which is a different weapon from the one the "
                        + "number describes");
        assertEquals(MaceFall.MAX_DAMAGE, MaceFall.damageFor(400.0F),
                "four hundred blocks of falling is still twenty two");
    }

    /**
     * Vanilla's shape, scaled.
     *
     * <p><b>Three points of the borrowed curve, at the points it changes slope.</b> They are
     * asserted as fractions of the ceiling so the test says "this is vanilla's curve, smaller"
     * rather than six decimals that look like a measurement.</p>
     */
    @Test
    @DisplayName("the shape is vanilla's, at half the size")
    void theShapeIsVanillas() {
        float scale = MaceFall.SCALE;

        assertEquals(0.0F, MaceFall.bonusFor(MaceFall.THRESHOLD));
        // Vanilla at the knee: 4 * 3 = 12 out of 22.
        assertEquals(12.0F * scale, MaceFall.bonusFor(3.0F), 0.0001F,
                "at three blocks vanilla is worth twelve of its twenty two, so here it is "
                        + "twelve of ours");
        // Vanilla at the tail: 12 + 2 * (8 - 3) = 22 out of 22, scaled to twelve.
        assertEquals(12.0F, MaceFall.bonusFor(8.0F), 0.0001F,
                "**eight blocks is the knee, and it is twelve.** This is the number the split "
                        + "protects: the borrowed curve ends here and our own tail takes over, so "
                        + "raising the ceiling cannot move any point below it");

        // **And two points INSIDE the segments, which is what the three above cannot do.**
        //
        // <p>The first version of this test asserted the threshold, the knee and the tail &mdash;
        // three nodes &mdash; and replacing the whole three-segment curve with a single straight
        // line through them left it green. Which is the general shape of the mistake: three points
        // do not determine a curve, and any assertion made only of the points a curve changes slope
        // at is asserting a spline, and there are infinitely many.</p>
        //
        // <p>Two blocks is inside the steep first segment, where vanilla climbs at four a block.
        // Five is inside the middle one, where it climbs at two. The straight line that passes all
        // three nodes climbs at 1.85 throughout, so it is short at two by nearly half and short at
        // five by a quarter.</p>
        assertEquals(8.0F * scale, MaceFall.bonusFor(2.0F), 0.0001F,
                "two blocks in: vanilla is four a block here, so eight of its twelve. **This is "
                        + "the point that catches a straight line**, which reaches only six and a "
                        + "half while passing through every node the other three assertions use");
        assertEquals(16.0F * scale, MaceFall.bonusFor(5.0F), 0.0001F,
                "and five blocks in: vanilla has dropped to two a block by then, so sixteen of "
                        + "its twenty two. A single line is short here too, by a quarter");

        // **And the tail, which is ours.** One point a block from the knee, which is vanilla's own
        // slope at a third of the distance -- see the class comment on why the borrowed one cannot
        // reach a ceiling this high.
        assertEquals(12.0F + 4.0F, MaceFall.bonusFor(12.0F), 0.0001F,
                "twelve at the knee plus four more blocks at one apiece");
        assertEquals(12.0F + 9.0F, MaceFall.bonusFor(17.0F), 0.0001F,
                "and nine more at seventeen. **The tail is a straight line and it has to be**: "
                        + "vanilla's own is, and the alternative is a curve nobody tuned");
    }

    /**
     * It only ever rises.
     *
     * <p>A drop in the curve would mean a fall can be worth less than a shorter one, and no player
     * could be told what the number means.</p>
     */
    @Test
    @DisplayName("more fall is never worth less")
    void itOnlyRises() {
        float previous = -1.0F;
        for (float fall = 0.0F; fall <= 400.0F; fall += 0.25F) {
            float bonus = MaceFall.bonusFor(fall);
            assertTrue(bonus >= previous - 0.0001F,
                    "the curve went backwards between " + (fall - 0.25F) + " and " + fall
                            + " blocks: " + previous + " then " + bonus);
            previous = bonus;
        }
    }

        /**
     * The tail is observable now, and the height it produces is asserted.
     *
     * <h2>What this test used to say, and why it changed</h2>
     *
     * <p>It used to assert that the tail <b>could not be observed</b>: with a ceiling of twelve the
     * curve was already at the cap by eight blocks, so everything above was clamped and removing
     * the tail left the suite green. It said so in as many words -- "the day someone raises
     * {@link MaceFall#BONUS_CEILING} and the tail starts to matter, this fails and says so".</p>
     *
     * <p>The ceiling was raised, this failed, and it was right. The tail is now the whole second
     * half of the weapon: it is what carries twelve at the knee up to thirty five, and the height
     * where it gets there is <b>derived</b> rather than chosen.</p>
     */
    @Test
    @DisplayName("the cap lands at 31 blocks, and the height is derived from the ceiling")
    void theCapHasAHeight() {
        assertEquals(31.0F, MaceFall.CAP_FALL, 0.0001F,
                "**the height fifty damage needs.** The playtest asked for the top of the weapon to "
                        + "reach fifty and for the height to move with it, and this is the number "
                        + "that falls out of the curve: twelve at the knee, one a block, thirteen "
                        + "blocks to go");
        assertEquals(8.0F + (MaceFall.BONUS_CEILING - 12.0F), MaceFall.CAP_FALL, 0.0001F,
                "**and it is derived, not written down twice.** `knee + (ceiling - knee_bonus)`, so "
                        + "raising the ceiling moves the height by itself and the two cannot drift");

        assertEquals(MaceFall.BONUS_CEILING, MaceFall.bonusFor(MaceFall.CAP_FALL), 0.0001F,
                "the cap is reached exactly there");
        assertTrue(MaceFall.bonusFor(MaceFall.CAP_FALL - 1.0F) < MaceFall.BONUS_CEILING,
                "and not one block before it, or the height is a lie");
        assertEquals(MaceFall.BONUS_CEILING, MaceFall.bonusFor(MaceFall.CAP_FALL + 40.0F), 0.0001F,
                "and it stays there, because the ceiling is a ceiling");
    }

/** One weapon reads a fall, and the check is of the profile rather than of a list. */
    @Test
    @DisplayName("only the maza reads a fall")
    void onlyTheMace() {
        assertTrue(MaceFall.reads(com.cortes.combat.core.weapon.WeaponProfile.MACE));
        for (com.cortes.combat.core.weapon.WeaponProfile profile
                : com.cortes.combat.core.weapon.WeaponProfile.values()) {
            if (profile != com.cortes.combat.core.weapon.WeaponProfile.MACE) {
                assertFalse(MaceFall.reads(profile), profile + " reads a fall and should not");
            }
        }
    }

    /**
     * The smash gate, which is vanilla's and not "is this the maza".
     *
     * <p>Same threshold as the bonus, and the gliding exclusion is the half a naive
     * {@code fallDistance > 1.5} drops: an elytra fall is a fall in the variable and not one the
     * weapon may smash on, and vanilla says so in {@code canSmashAttack}. It is the gate that
     * decides whether the landing states are armed at all, so a wrong answer arms the fall immunity
     * for a hit that was not a smash.</p>
     */
    @Test
    @DisplayName("a smash is a fall, and never a glide")
    void smashesOnlyWhenFalling() {
        assertFalse(MaceFall.smashes(MaceFall.THRESHOLD, false),
                "the threshold itself is not a smash, exactly as it is not a bonus");
        assertTrue(MaceFall.smashes(MaceFall.THRESHOLD + 0.01F, false),
                "just past it is");
        assertFalse(MaceFall.smashes(0.0F, false), "and no fall never is");
        assertFalse(MaceFall.smashes(40.0F, true),
                "**a gliding player is never a smash**, which is vanilla's `!isFallFlying` -- the "
                        + "same exclusion `MaceAir.airborne` makes for the reach");
    }
}
