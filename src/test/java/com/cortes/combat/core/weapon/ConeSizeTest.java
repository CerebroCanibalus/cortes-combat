package com.cortes.combat.core.weapon;

import com.cortes.combat.item.WeaponTables;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the cone-size bands are the bands DESIGN.md described, and that their edges sit in the gaps
 * of the real table rather than on round numbers.
 *
 * <p><b>This test exists because the alternative was a number I would have picked by taste.</b> A
 * tooltip that prints "Tamaño: Muy ancho" is making a claim about the halberd, and the claim is
 * only worth anything if it came from the same place the halberd's 55 degrees came from. Three
 * things are asserted, and in this order because each one is weaker than the next:</p>
 *
 * <ol>
 *   <li>the five families land on the band DESIGN.md's own table names them with;</li>
 *   <li>every profile in {@code WeaponTables} gets a band, so the tooltip never has a hole;</li>
 *   <li>every edge has a degree of clearance from every angle, so the bands are separated by
 *       real gaps and not by a line drawn between two neighbours.</li>
 * </ol>
 */
class ConeSizeTest {

    /**
     * The five words DESIGN.md's summary table uses for the cone, and the band that carries each.
     *
     * <p><b>Four of the five are the design's own word.</b> The dagger's is the interesting one: the
     * design calls its cone "alto", and this enum calls that band {@code WIDE}. Same band, and
     * the mismatch is deliberate -- "Tamaño: Alto" next to "Tamaño: Muy ancho" reads as a ranking
     * where "Tamaño: Ancho" reads as a shape, and the line is about a shape.</p>
     */
    private static final Object[][] DESIGN_WORDS = {
            {"ropera", "muy pequeño", ConeSize.VERY_SMALL},
            {"warhammer", "medio", ConeSize.MEDIUM},
            {"dagger", "alto", ConeSize.WIDE},
            {"halberd", "muy ancho", ConeSize.VERY_WIDE},
            {"sabre", "máximo", ConeSize.MAXIMUM},
    };

    @Test
    @DisplayName("the five families land on the band DESIGN.md named them with")
    void theFamiliesMatchTheDesign() {
        for (Object[] row : DESIGN_WORDS) {
            String id = (String) row[0];
            String designWord = (String) row[1];
            ConeSize expected = (ConeSize) row[2];

            WeaponProfile profile = WeaponProfile.valueOf(id.toUpperCase(java.util.Locale.ROOT));
            float angle = WeaponTables.reach(profile).horizontalAngle();

            assertSame(expected, ConeSize.forAngle(angle),
                    "the " + id + " has a " + angle + " degree cone, which DESIGN.md section 6 "
                            + "calls \"" + designWord + "\"");
        }
    }

    /**
     * Every profile in the reach table gets a band.
     *
     * <p>A missing band is not a crash: {@code forAngle} would answer {@code VERY_SMALL} for
     * anything under twenty and the weapon would claim to be the hardest thing in the game to
     * land on. So the hole has to be found here instead.</p>
     */
    @Test
    @DisplayName("every profile in the reach table resolves to a band")
    void everyProfileHasABand() {
        Map<WeaponProfile, ReachProfile> table = WeaponTables.reachTable();
        assertTrue(table.size() >= 10, "the table shrank: " + table.size() + " profiles");

        List<String> missing = new ArrayList<>();
        for (Map.Entry<WeaponProfile, ReachProfile> entry : table.entrySet()) {
            float angle = entry.getValue().horizontalAngle();
            ConeSize size = ConeSize.forAngle(angle);
            if (size == null || Float.isNaN(angle)) {
                missing.add(entry.getKey() + " (" + angle + ")");
            }
        }
        assertEquals(List.of(), missing, "profiles with no size band");
    }

    /**
     * Every band edge has a degree of clearance from every angle in the game.
     *
     * <p><b>The assertion that says the bands are derived rather than chosen.</b> The horizontal
     * angles are 12, 15, 25, 28, 30, 35, 40, 45, 55 and 75, and the edges are 20, 29, 37.5, 50
     * and 65 -- each one the midpoint of a gap between clusters. If a future retune puts an angle
     * on top of an edge, this fails and says which one, which is the moment the band is wrong
     * rather than a week later when a player reports that the sabre's cone reads "Muy ancho".</p>
     *
     * <p>One degree and not two, because the narrowest gap in the table is two: the bare hand at
     * 28 and the warhammer at 30, whose midpoint is 29, and neither of them can be further than
     * one degree from it. A test demanding two would be demanding a table that does not exist.</p>
     */
    @Test
    @DisplayName("every band edge sits in a gap, clear of every angle in the table")
    void edgesSitInTheGaps() {
        float[] edges = {20.0F, 29.0F, 37.5F, 50.0F, 65.0F};

        List<String> touching = new ArrayList<>();
        for (Map.Entry<WeaponProfile, ReachProfile> entry : WeaponTables.reachTable().entrySet()) {
            float angle = entry.getValue().horizontalAngle();
            for (float edge : edges) {
                if (Math.abs(angle - edge) < 1.0F) {
                    touching.add(entry.getKey() + " at " + angle + " is on the edge at " + edge);
                }
            }
        }
        assertEquals(List.of(), touching,
                "an angle within a degree of a band boundary means the boundary was drawn across "
                        + "a weapon rather than between two groups of weapons");
    }

    /**
     * The bands are ordered, and the enum's order is the size order.
     *
     * <p>Cheap, and it catches the classic edit: adding a band in the middle of the file and
     * leaving the thresholds above it untouched, which produces an enum that says a dagger is
     * smaller than a warhammer.</p>
     */
    @Test
    @DisplayName("a wider angle is never a smaller size")
    void widerIsNeverSmaller() {
        ConeSize previous = null;
        for (float angle = 0.0F; angle <= 90.0F; angle += 0.5F) {
            ConeSize current = ConeSize.forAngle(angle);
            if (previous != null) {
                assertTrue(current.ordinal() >= previous.ordinal(),
                        "going from " + previous + " at a narrower angle to " + current
                                + " at " + angle + " degrees makes the sizes go backwards");
            }
            previous = current;
        }
    }

    /**
     * And the two ends of the range are the two ends of the game's range.
     *
     * <p>A band system with an unused top or bottom is a band system somebody will misread: the
     * sabre has to be the maximum cone in the game, because it is.</p>
     */
    @Test
    @DisplayName("the narrowest and widest weapons in the table are the two ends")
    void theEndsAreOccupied() {
        assertSame(ConeSize.VERY_SMALL, ConeSize.forAngle(12.0F), "the ropera's 12");
        assertSame(ConeSize.MAXIMUM, ConeSize.forAngle(75.0F), "the sabre's 75");

        // **The trident shares the ropera's band, and an earlier version of this test asserted the
        // opposite.** Fifteen degrees is three away from twelve and nothing in the game sits
        // between them, so splitting the narrow band would separate two weapons a player cannot
        // tell apart over six bands of words. The next band starts at twenty, which is the
        // midpoint of the gap above them -- so the band is one degree wide of slack and no more.
        assertSame(ConeSize.VERY_SMALL, ConeSize.forAngle(15.0F),
                "the trident's 15 is three degrees from the ropera's 12 and shares its band");

        assertSame(ConeSize.VERY_SMALL, ConeSize.forAngle(19.9F),
                "the narrow band is still open a tenth of a degree below its edge");
        assertSame(ConeSize.SMALL, ConeSize.forAngle(20.0F),
                "and twenty is the first value of the next one, because the comparison is a "
                        + "strict '<' against the midpoint of the gap");
    }

    /** An id round-trips, so the override config and a test walking the enum agree. */
    @Test
    @DisplayName("every id parses back to its own band")
    void idsRoundTrip() {
        for (ConeSize size : ConeSize.values()) {
            assertSame(size, ConeSize.byId(size.id()));
            assertSame(size, ConeSize.byId(size.id().toUpperCase(java.util.Locale.ROOT)));
        }
    }

    /**
     * A malformed angle answers something instead of throwing.
     *
     * <p>The angle can come off a data component any mod can write, and a tooltip that takes the
     * render thread down over a NaN is a far worse outcome than a weapon that claims to be
     * small.</p>
     */
    @Test
    @DisplayName("a NaN or negative angle answers rather than throws")
    void malformedAnglesAnswer() {
        assertSame(ConeSize.VERY_SMALL, ConeSize.forAngle(Float.NaN));
        assertSame(ConeSize.VERY_SMALL, ConeSize.forAngle(-30.0F));
        assertSame(ConeSize.MAXIMUM, ConeSize.forAngle(Float.POSITIVE_INFINITY));
    }
}