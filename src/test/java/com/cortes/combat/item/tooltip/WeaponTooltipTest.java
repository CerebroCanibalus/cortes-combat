package com.cortes.combat.item.tooltip;

import com.cortes.combat.attack.DamageResolver;
import com.cortes.combat.core.weapon.ChargeProfile;
import com.cortes.combat.core.weapon.ConeSize;
import com.cortes.combat.core.weapon.ReachProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.item.WeaponKinds;
import com.cortes.combat.item.WeaponTables;
import com.cortes.combat.particle.SlashTiming;
import com.cortes.combat.state.StreakState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the tooltip says what the game does.
 *
 * <p><b>These tests are the reason the tooltip is a pure class.</b> Every one of them is a
 * question that could only otherwise be answered by launching the game and looking at the screen:
 * does a block get a tooltip, does the sabre's charged line count both cuts, does a weapon that
 * cannot charge advertise a charge. The moment the numbers were written next to the event handler
 * all three of those became "ask a playtester", and a playtester reports symptoms rather than
 * numbers.</p>
 */
class WeaponTooltipTest {

    /** The sabre's real charge profile, which is the one that lands two hits. */
    private static final ChargeProfile SABRE_CHARGE = WeaponTables.charge(WeaponProfile.SABRE);

    private static List<TooltipLine> linesFor(WeaponProfile profile, float attackDamage,
                                             int intervalTicks, int maxDamage) {
        return WeaponTooltip.linesFor(profile, attackDamage, intervalTicks,
                WeaponTables.reach(profile), WeaponTables.charge(profile),
                WeaponTables.special(profile), WeaponTables.knockback(profile),
                WeaponTables.maxTargets(profile), maxDamage, maxDamage,
                !WeaponTables.shotReach(profile).isNone(), WeaponKinds.fights(profile),
                com.cortes.combat.state.ArcabuzState.RELOAD_TICKS);
    }

    /**
     * A block gets nothing.
     *
     * <p>The assertion is about the whole list rather than about one line: printing "3 damage,
     * small cone" under every dirt block in the inventory is how a tooltip becomes wallpaper, and
     * wallpaper is worse than no tooltip because it teaches the player not to read the ones that
     * matter.</p>
     */
    @Test
    @DisplayName("a block gets no tooltip at all")
    void aBlockGetsNothing() {
        assertEquals(List.of(),
                WeaponTooltip.linesFor(WeaponProfile.NONE, 1.0F, 20,
                        WeaponTables.reach(WeaponProfile.NONE),
                        WeaponTables.charge(WeaponProfile.NONE),
                        SpecialType.NONE, WeaponTables.knockback(WeaponProfile.NONE),
                        1, 0, 0, false, false,
                        com.cortes.combat.state.ArcabuzState.RELOAD_TICKS),
                "NONE is not a weapon. Printing numbers on it is the tooltip form of the bug that "
                        + "made a dirt block inherit a sword's charge");

        assertEquals(List.of(), linesFor(WeaponProfile.NONE, 1.0F, 20, 100),
                "and that holds even when the stack has a durability, which a block does not");
    }

    /**
     * A weapon that cannot charge does not get a charged line.
     *
     * <p>The ropera and the dagger are the two in the set where this matters most: they are the
     * fast weapons a player reaches for first, and a "daño cargado" line next to their damage
     * would be promising a bonus the table says cannot happen.</p>
     */
    @Test
    @DisplayName("a weapon that cannot charge never claims a charged damage")
    void nonChargingWeaponsHaveNoChargedLine() {
        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.ROPERA, WeaponProfile.DAGGER, WeaponProfile.ARCABUZ}) {
            assertFalse(WeaponTables.charge(profile).canCharge(),
                    profile + " is supposed to be a non-charging weapon; if this fails the tooltip "
                            + "test below is asserting the wrong thing");
            for (TooltipLine line : linesFor(profile, 6.0F, 10, 250)) {
                assertFalse(line.key().endsWith("charged_damage"),
                        profile + " cannot charge and its tooltip says it can");
            }
        }
    }

    /**
     * And one that can, gets one that is arithmetically the game's own.
     *
     * <p>Asserted against {@code DamageResolver} rather than against a literal, because the
     * tooltip's job is to be a readout: if the formula changes, this test follows it and the
     * tooltip stays true. A test with a hardcoded 9.0 would go red on a balance retune and
     * somebody would "fix" the tooltip instead of reading why.</p>
     */
    @Test
    @DisplayName("the charged line is the swing's real charged damage")
    void theChargedLineIsTheGamesOwnNumber() {
        float damage = 6.0F;
        WeaponProfile profile = WeaponProfile.WARHAMMER;
        ChargeProfile charge = WeaponTables.charge(profile);

        float expected = DamageResolver.resolve(damage, charge, profile,
                DamageResolver.FULL_CHARGE, StreakState.EMPTY).damage();

        String printed = argumentOf(linesFor(profile, damage, 19, 250),
                "tooltip.cortescombat.charged_damage");

        assertEquals(WeaponTooltip.decimal(expected), printed,
                "the charged line must be what DamageResolver computes, or it is a second opinion "
                        + "about the game's damage");
    }

    /**
     * The sabre's charged line counts <b>both</b> cuts.
     *
     * <p>This is the one that would have shipped looking fine. Per-cut charged damage is half the
     * real total, and half of a big number still looks like a big number on a tooltip -- so
     * nothing would look wrong until a player compared the line against a kill. A full charge on
     * the sabre is <b>four</b> times base, not two, because two cuts each carry the multiplier.</p>
     */
    @Test
    @DisplayName("the sabre's charged damage is both cuts, so four times base and not two")
    void theSabreCountsBothCuts() {
        float damage = 7.0F;
        float perCut = DamageResolver.resolve(damage, SABRE_CHARGE, WeaponProfile.SABRE,
                DamageResolver.FULL_CHARGE, StreakState.EMPTY).damage();
        int cuts = SlashTiming.hitsAt(WeaponProfile.SABRE, SABRE_CHARGE,
                DamageResolver.FULL_CHARGE);
        assertEquals(2, cuts, "the sabre lands two at a full charge");

        String printed = argumentOf(linesFor(WeaponProfile.SABRE, damage, 10, 250),
                "tooltip.cortescombat.charged_damage");

        assertEquals(WeaponTooltip.decimal(perCut * cuts), printed,
                "a full charge on the sabre is four times base, not two. Printing the per-cut "
                        + "number would promise " + WeaponTooltip.decimal(perCut) + " and deliver "
                        + WeaponTooltip.decimal(perCut * cuts));

        // And the uncharged line is the single cut, because only a full charge doubles.
        assertEquals(WeaponTooltip.decimal(damage), argumentOf(
                        linesFor(WeaponProfile.SABRE, damage, 10, 250),
                        "tooltip.cortescombat.damage"),
                "the base line is one cut: the second only exists at a full charge");
    }

    /**
     * The streak is not in the warhammer's numbers.
     *
     * <p>A tooltip has no player attached to it and so no streak to report. Printing a
     * streak-assuming number would be promising something that depends on a state the reader
     * might not be in.</p>
     */
    @Test
    @DisplayName("the warhammer's charged damage assumes no streak")
    void theWarhammerChargedDamageHasNoStreak() {
        float damage = 7.0F;
        ChargeProfile charge = WeaponTables.charge(WeaponProfile.WARHAMMER);

        float firstHit = DamageResolver.resolve(damage, charge, WeaponProfile.WARHAMMER,
                DamageResolver.FULL_CHARGE, StreakState.EMPTY).damage();
        float thirdHit = DamageResolver.resolve(damage, charge, WeaponProfile.WARHAMMER,
                DamageResolver.FULL_CHARGE, new StreakState(2, 0)).damage();

        assertTrue(thirdHit > firstHit, "a streak really does raise the damage");
        assertEquals(WeaponTooltip.decimal(firstHit), argumentOf(
                        linesFor(WeaponProfile.WARHAMMER, damage, 19, 250),
                        "tooltip.cortescombat.charged_damage"),
                "the tooltip prints the first hit's number, because that is the weapon and not "
                        + "the reader's current combo");
    }

    /**
     * The lines come out in the order they read in.
     *
     * <p>Pinned as an exact list of keys rather than as a set, because the order is a reading
     * order -- how much, how fast, how far, how wide, what the button does, how long it lasts --
     * and a set cannot tell an order change from a reorder.</p>
     */
    @Test
    @DisplayName("a full weapon reads category, damage, charge, speed, range, size, special, durability")
    void theLinesAreInReadingOrder() {
        // **The halberd, and picking the wrong weapon here cost a round.** It was written with the
        // ropera, which turns out to be the second weapon in the set that does not charge and
        // whose cone is the narrowest in the game -- so it has no charged line and its size is
        // "very_small", and the expected list was wrong twice in the two places that mattered.
        //
        // The halberd is the fullest case: it charges, it has a right button, it has durability,
        // its 55 degrees put it in "very wide", and its five block reach makes it a long weapon.
        assertEquals(List.of(
                        "tooltip.cortescombat.category.long",
                        "tooltip.cortescombat.damage",
                        "tooltip.cortescombat.charged_damage",
                        "tooltip.cortescombat.attack_speed",
                        "tooltip.cortescombat.range",
                        "tooltip.cortescombat.size.very_wide",
                        "tooltip.cortescombat.knockback",
                        "tooltip.cortescombat.charged_knockback",
                        "tooltip.cortescombat.special.multi_hit",
                        "tooltip.cortescombat.durability",
                        "tooltip.cortescombat.mechanic.halberd"),
                linesFor(WeaponProfile.HALBERD, 8.0F, 18, 250).stream()
                        .map(TooltipLine::key).toList(),
                "the reading order is what it is, how much, how fast, how far, how wide, what the "
                        + "button does, how long it lasts -- **and the category first**, because "
                        + "that is where vanilla puts its own and because it frames every line "
                        + "under it: 'long weapon' is what tells the player that a five block "
                        + "reach is the weapon working as intended");
    }

    /**
     * The size is a whole key, never a key plus an argument.
     *
     * <p>A {@code %s} argument is rendered literally, so "Tamaño: %s" with {@code very_small} as
     * its argument would put the English identifier on screen. The band has to be the key.</p>
     */
    @Test
    @DisplayName("the cone size is its own key and carries no argument")
    void theConeSizeIsAWholeKey() {
        TooltipLine size = lineFor(WeaponProfile.SABRE, "tooltip.cortescombat.size.maximum");

        assertEquals(List.of(), size.args(),
                "the band went in as an argument, which would render as the literal \"maximum\"");

        // And every profile in the table resolves to a key that is one of the six.
        for (WeaponProfile profile : WeaponProfile.values()) {
            // **And not a tool that does not fight.** The pickaxe has a reach row but no size
            // line any more: a non-fighting tool stops at its category, so there is no band to
            // resolve. Skipping it here is the test following the same rule the code does.
            if (profile == WeaponProfile.NONE || !WeaponKinds.fights(profile)) {
                continue;
            }
            ConeSize band = ConeSize.forAngle(
                    WeaponTables.reach(profile).horizontalAngle());
            assertTrue(lineFor(profile, "tooltip.cortescombat.size." + band.id()).key()
                            .equals("tooltip.cortescombat.size." + band.id()),
                    profile + " produced no size line for its band " + band.id());
        }
    }

    /**
     * A tool that does not fight gets its category and its durability, and nothing else.
     *
     * <h2>Why this is a separate shape and not "NONE gets nothing"</h2>
     *
     * <p>The pickaxe is the case: it has a reach row, a charge row and a knockback row like every
     * other profile, and none of them is reachable from a click since it lost combat. Printing them
     * would be a range a player reads off a tool that cannot use it -- the exact "a number the
     * player reads and the game does not produce" the whole tooltip was rewritten to stop. What is
     * left is the two facts that are true of it: what it is, and how much of it is left.</p>
     *
     * <p><b>And a tool that DOES fight is untouched.</b> The pala and the hoe share the tool
     * category and still get the full weapon block, because they do swing -- the split is
     * {@code WeaponKinds.fights}, not {@code WeaponCategory.isTool}.</p>
     */
    @Test
    @DisplayName("a tool that does not fight gets its category and durability and no weapon lines")
    void aNonFightingToolHasOnlyCategoryAndDurability() {
        assertEquals(List.of(
                        "tooltip.cortescombat.category.tool",
                        "tooltip.cortescombat.durability"),
                linesFor(WeaponProfile.PICKAXE, 6.0F, 20, 250).stream()
                        .map(TooltipLine::key).toList(),
                "the pickaxe has a reach and a cone in the table, and neither is reachable from a "
                        + "click, so neither may be printed. What is true of it is 'Herramienta' "
                        + "and its durability");

        // The same category, a fighting tool, and the full block.
        assertTrue(linesFor(WeaponProfile.SHOVEL, 6.0F, 20, 250).size() > 2,
                "the pala is a tool too, but it fights -- so it keeps every weapon line");
        assertEquals("tooltip.cortescombat.category.tool",
                linesFor(WeaponProfile.SHOVEL, 6.0F, 20, 250).get(0).key(),
                "and it still reads as a tool, because that is what it is");
    }

    /** Durability is printed when there is some and omitted when there is not. */
    @Test
    @DisplayName("durability appears only for an item that can break")
    void durabilityOnlyWhenThereIsSome() {
        assertEquals("250/250", argumentOf(linesFor(WeaponProfile.ROPERA, 6.0F, 10, 250),
                "tooltip.cortescombat.durability"),
                "**a counter, not a maximum.** The playtest asked for 'DUR. ACTUAL/Max.' and the "
                        + "reason is that a number which never changes is not a stat: a sword at "
                        + "190/250 and one at 4/250 printed the same line, and the one moment the "
                        + "number matters is the moment it is nearly gone");

        for (TooltipLine line : linesFor(WeaponProfile.ROPERA, 6.0F, 10, 0)) {
            assertFalse(line.key().endsWith("durability"),
                    "\"Durabilidad: 0\" on something that cannot break is worse than silence");
        }
    }

    /** The right button is described only by the weapons that have one. */
    @Test
    @DisplayName("the special line appears only where a special exists")
    void theSpecialLineIsConditional() {
        assertTrue(lineFor(WeaponProfile.HALBERD, "tooltip.cortescombat.special.multi_hit")
                        .key().equals("tooltip.cortescombat.special.multi_hit"),
                "the halberd's sweep has to say so");

        for (WeaponProfile profile : new WeaponProfile[]{
                WeaponProfile.SWORD, WeaponProfile.DAGGER, WeaponProfile.WARHAMMER,
                WeaponProfile.NONE}) {
            assertFalse(WeaponTables.special(profile).isPresent(),
                    profile + " is supposed to have no right button");
            for (TooltipLine line : linesFor(profile, 6.0F, 10, 250)) {
                assertFalse(line.key().contains("special."),
                        profile + " has no right button and its tooltip advertises one");
            }
        }
    }

    /**
     * Every number is one decimal, and none of them is an exponent.
     *
     * <p>Directly the bug this guards: the arithmetic runs on floats, and a value like
     * {@code 6.949999809265137} is what {@code String.valueOf} prints. A damage number with
     * fifteen decimals is not a number a player can read and not one the project can defend.</p>
     */
    @Test
    @DisplayName("no number carries more than one decimal or an exponent")
    void numbersAreReadable() {
        for (WeaponProfile profile : WeaponProfile.values()) {
            for (float damage : new float[]{0.0F, 1.0F, 4.5F, 6.9499998F, 7.0F, 12.75F}) {
                for (TooltipLine line : linesFor(profile, damage, 8, 250)) {
                    for (String argument : line.args()) {
                        // No letter at all: every argument in this tooltip is a number, so an "E"
                        // is Java's exponent notation and "NaN" or "INF" leaked through.
                        assertFalse(containsLetter(argument),
                                profile + " printed \"" + argument + "\", which is not a number");
                        int dot = argument.indexOf('.');
                        assertEquals(-1, argument.indexOf('.', dot + 1),
                                profile + " printed " + argument + ", which has more than one "
                                        + "decimal point");
                        // Zero decimals is legal and is what durability is: an integer argument
                        // has no point at all, and counting the characters after one that is not
                        // there is how this test failed the first time with "3 decimals" on "250".
                        int decimals = dot < 0 ? 0 : argument.length() - dot - 1;
                        assertTrue(decimals <= 1,
                                profile + " printed " + argument + ", which has " + decimals
                                        + " decimals");
                    }
                }
            }
        }
    }

    /**
     * The speed line is attacks per second, and it is the inverse of the interval.
     *
     * <p>Pinned because it is the one line whose unit is a decision: the game counts in ticks and
     * vanilla's own attribute is attacks per second, and mixing them means a reader has to hold
     * two conversions in their head.</p>
     */
    @Test
    @DisplayName("attack speed is attacks per second, not ticks")
    void attackSpeedIsPerSecond() {
        assertEquals("2.0", argumentOf(linesFor(WeaponProfile.ROPERA, 6.0F, 10, 250),
                "tooltip.cortescombat.attack_speed"), "ten ticks is two a second");
        assertEquals("5.0", argumentOf(linesFor(WeaponProfile.UNARMED, 1.0F, 4, 0),
                "tooltip.cortescombat.attack_speed"), "four ticks is five a second");
        assertEquals(20.0F / 24.0F,
                WeaponTooltip.attacksPerSecond(24), 1.0E-6);
    }

    /** A zero interval is answered rather than being a division by zero on the render thread. */
    @Test
    @DisplayName("a nonsensical interval answers instead of throwing")
    void aZeroIntervalAnswers() {
        assertEquals(0.0F, WeaponTooltip.attacksPerSecond(0), 1.0E-6);
        assertEquals(0.0F, WeaponTooltip.attacksPerSecond(-5), 1.0E-6);
    }

    /** A missing reach is treated as "not a weapon" rather than dereferenced. */
    @Test
    @DisplayName("a null reach answers with nothing instead of throwing")
    void aNullReachAnswers() {
        assertEquals(List.of(),
                WeaponTooltip.linesFor(WeaponProfile.SWORD, 6.0F, 10, null,
                        WeaponTables.charge(WeaponProfile.SWORD),
                        WeaponTables.special(WeaponProfile.SWORD),
                        WeaponTables.knockback(WeaponProfile.SWORD), 1, 250, 250, false, true,
                com.cortes.combat.state.ArcabuzState.RELOAD_TICKS),
                "the reach comes off a component any mod can write; a null one must not take the "
                        + "render thread down");
    }

    // ------------------------------------------------------------------ helpers --

    /**
     * Whether a printed argument contains any letter at all.
     *
     * <p>Every argument this tooltip emits is a number, so a letter can only be Java's exponent
     * marker, a {@code NaN}, or an {@code INF} from the formatter's escape hatch. Written by hand
     * rather than with a regex because the assertion reads better as "is not a number" than as a
     * character class, and because {@code String.matches} recompiles the pattern on every call in
     * a loop that walks every profile.</p>
     */
    private static boolean containsLetter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isLetter(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static TooltipLine lineFor(WeaponProfile profile, String key) {
        for (TooltipLine line : linesFor(profile, 6.0F, 10, 250)) {
            if (line.key().equals(key)) {
                return line;
            }
        }
        throw new AssertionError("no line with key " + key + " for " + profile);
    }

    private static String argumentOf(List<TooltipLine> lines, String key) {
        return lineFor(lines, key).args().get(0);
    }

    private static TooltipLine lineFor(List<TooltipLine> lines, String key) {
        for (TooltipLine line : lines) {
            if (line.key().equals(key)) {
                return line;
            }
        }
        throw new AssertionError("no line with key " + key + ", got " + lines);
    }
}