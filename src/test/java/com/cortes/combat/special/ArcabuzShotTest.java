package com.cortes.combat.special;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arcabuz's balance, as arithmetic, on a bare JVM.
 *
 * <p>Every claim the design makes about this weapon is a number: it caps at ten targets, it hurts
 * a lot, it burns. Those are exactly the claims a playtest cannot check precisely -- "the damage
 * felt high" is true of nine and of fifteen -- and exactly the ones a unit test can. This class
 * exists because {@code ArcabuzShot} is pure for that reason.</p>
 *
 * <p>It reads {@link com.cortes.combat.item.ArcabuzStats} for the weapon's damage rather than
 * repeating it, so the swing and the shot are checked against one number.</p>
 */
class ArcabuzShotTest {

    private static List<String> crowd(int size) {
        List<String> found = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            found.add("mob" + i);
        }
        return found;
    }

    @Test
    @DisplayName("the cap is ten, and it is a cap rather than a suggestion")
    void tenTargetsHard() {
        assertEquals(10, ArcabuzShot.MAX_TARGETS,
                "the design says 'limite de 10 objetivos'. Read with the damage: a wide cone with "
                        + "ordinary damage is a crowd weapon like the halberd's, and high damage "
                        + "with no cap is a delete button aimed at whatever the player faced");
    }

    /**
     * The cap takes the NEAREST ten, and that is a property rather than a coincidence.
     *
     * <p>{@code targetsInCone} sorts nearest first and this takes a prefix of it, so when the cap
     * bites the survivors are whatever was behind the blast. That is what makes the cap legible
     * to play: the player has to step to the side to hit the rest, rather than stand still and
     * watch a wall of husks.</p>
     */
    @Test
    @DisplayName("the cap keeps the nearest ten and drops what is behind them")
    void theCapKeepsTheNearest() {
        List<String> thirty = crowd(30);
        List<String> hit = ArcabuzShot.capped(thirty);

        assertEquals(10, hit.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("mob" + i, hit.get(i),
                    "nearest first, so the tenth nearest is hit and the eleventh is not");
        }
        assertTrue(!hit.contains("mob10"),
                "the eleventh is the first one the cap drops. If it were in here, the cap would "
                        + "be picking a subset rather than a prefix and the survivor would be "
                        + "arbitrary");
    }

    @Test
    @DisplayName("a crowd of ten or fewer is not trimmed, and neither is an empty one")
    void shortCrowdsAreUntouched() {
        assertEquals(10, ArcabuzShot.capped(crowd(10)).size());
        assertEquals(9, ArcabuzShot.capped(crowd(9)).size());
        assertEquals(List.of(), ArcabuzShot.capped(List.of()),
                "a blast into empty air is a blast, and it still costs the firestone and the "
                        + "second. That is the halfberd's rule and the arcabuz's");
    }

    /**
     * The returned list is not the caller's.
     *
     * <p>A caller that kept a handle and sorted it afterwards would be reordering what the cap
     * chose, and the next thing to happen is a shot that hit the far end of the room.</p>
     */
    @Test
    @DisplayName("the cap hands back a list nobody can change")
    void theCapIsUnmodifiable() {
        List<String> capped = ArcabuzShot.capped(crowd(30));
        assertNotSame(crowd(30), capped);
        assertThrows(UnsupportedOperationException.class, () -> capped.add("extra"),
                "a caller could add an eleventh target to a list that is supposed to be the ten "
                        + "the cap chose");
    }

    /**
     * The swing's damage and the shot's damage are one number and one coefficient.
     *
     * <p>This is the whole reason {@code SHOT_MULTIPLIER} is a multiplier. An absolute twelve in
     * {@code ArcabuzShot} would have been a second place the weapon's damage is written down, and
     * the first of the two to be retuned would have been the one nobody noticed.</p>
     */
    @Test
    @DisplayName("the blast is the weapon's own damage times the coefficient")
    void theShotIsAMultipleOfTheWeapon() {
        float attackDamage = com.cortes.combat.item.ArcabuzStats.ATTACK_DAMAGE;
        assertEquals(2.0F, ArcabuzShot.SHOT_MULTIPLIER);
        assertEquals(attackDamage * 2.0F, ArcabuzShot.damageFor(attackDamage), 1.0E-6);
        assertEquals(ArcabuzShot.SHOT_MULTIPLIER * attackDamage,
                com.cortes.combat.item.ArcabuzStats.shotDamage(), 1.0E-6,
                "the item's own answer and the shot's answer are one multiplication, read twice");

        assertEquals(12.0F, ArcabuzShot.damageFor(6.0F), 1.0E-6,
                "and 6.0 of attack damage is what the arcabuz actually gives a player");
    }

    /**
     * "Damage alto", turned into the two numbers a playtest can check.
     *
     * <p>A husk has twenty health, so twelve kills one outright. Ten of them is a hundred and
     * twenty against a room, which is most of it and not all of it -- a blast that wiped the room
     * would have no reason to cost a firestone and a second.</p>
     */
    @Test
    @DisplayName("a full blast is a hundred and twenty, which kills a husk six times over")
    void theCeilingIsAHundredAndTwenty() {
        assertEquals(120.0F, ArcabuzShot.maximumDamage(6.0F), 1.0E-4);
        assertEquals(ArcabuzShot.SHOT_MULTIPLIER * 6.0F * ArcabuzShot.MAX_TARGETS,
                ArcabuzShot.maximumDamage(6.0F), 1.0E-4);
    }

    /**
     * Every target takes the same, and there is no falloff.
     *
     * <p>Written down as a test because it is a decision, not an omission. A shotgun's pellets
     * lose energy with range and adding that would be more realistic; it is not here because the
     * halberd's sweep does not do it either, and a blast whose damage depends on where the mob
     * happened to be standing is a number the tooltip cannot print. It is the first thing to add
     * if a playtest says the gun feels flat at range.</p>
     */
    @Test
    @DisplayName("the blast does not fall off with distance, and that is written down")
    void noFalloff() {
        float near = ArcabuzShot.damageFor(6.0F);
        float far = ArcabuzShot.damageFor(6.0F);
        assertEquals(near, far);
        assertEquals(ArcabuzShot.SHOT_MULTIPLIER * 6.0F, near, 1.0E-6,
                "one number, no index and no distance term. Same rule as the halberd's sweep");
    }

    @Test
    @DisplayName("six seconds of fire, which is a number and not a boolean")
    void sixSecondsOfFire() {
        assertEquals(6.0F, ArcabuzShot.FIRE_SECONDS, 1.0E-6);
        assertTrue(ArcabuzShot.FIRE_SECONDS > 1.0F,
                "a shot that did not leave things alive long enough to be on fire would be a hit "
                        + "plus an invisible debuff, and vanilla's onFire source only ticks damage "
                        + "once every twenty ticks");
    }

    /** The muzzle is not at the camera, or the flash is behind the camera. */
    @Test
    @DisplayName("the flash is drawn ahead of the eye, not on it")
    void theMuzzleIsAheadOfTheEye() {
        assertEquals(1.7, ArcabuzShot.MUZZLE_OFFSET, 1.0E-9,
                "at the tip of the barrel. Six tenths of a metre put it essentially at the "
                        + "camera, and the playtest called it exactly that: 'the muzzle flash "
                        + "comes out of the camera'. The gun's own art is twenty four units long "
                        + "in a sixty four unit texture, so the number that lands on the end of "
                        + "it is about a metre and a half -- derived from the drawn weapon rather "
                        + "than guessed a second time");
        assertTrue(ArcabuzShot.MUZZLE_OFFSET > 1.0,
                "and it has to be more than a metre. Anything shorter is still inside the "
                        + "player's own head, and a flash inside your own head is the screen "
                        + "flickering rather than a gun going off");
    }

    @Test
    @DisplayName("the count cannot go negative or past the cap")
    void theCountIsClamped() {
        assertEquals(0, ArcabuzShot.hitCount(-3));
        assertEquals(0, ArcabuzShot.hitCount(0));
        assertEquals(7, ArcabuzShot.hitCount(7));
        assertEquals(10, ArcabuzShot.hitCount(10));
        assertEquals(10, ArcabuzShot.hitCount(11));
        assertEquals(10, ArcabuzShot.hitCount(500),
                "a room of five hundred is still ten. That is the property that makes the weapon "
                        + "worth choosing on a bad day");
    }

    /** The list handed back for a short crowd is its own object too. */
    @Test
    @DisplayName("even a short crowd comes back as a copy")
    void evenAShortCrowdIsCopied() {
        List<String> nine = crowd(9);
        List<String> capped = ArcabuzShot.capped(nine);
        assertEquals(nine, capped);
        assertNotSame(nine, capped,
                "capped is a separate list even when nothing was trimmed, so the caller's sort "
                        + "order cannot be rewritten after the fact");
        assertThrows(UnsupportedOperationException.class, () -> capped.add("extra"));

        List<String> none = ArcabuzShot.capped(List.of());
        assertEquals(List.of(), none);
        assertThrows(UnsupportedOperationException.class, () -> none.add("extra"),
                "an empty crowd is still a list nobody can write to");
    }
}
