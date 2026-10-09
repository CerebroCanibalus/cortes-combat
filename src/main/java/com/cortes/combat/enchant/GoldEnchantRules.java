package com.cortes.combat.enchant;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gold's arithmetic, as numbers and not as stacks.
 *
 * <h2>Why the counts are computed here and the items elsewhere</h2>
 *
 * <p><b>An {@code ItemStack} is a Minecraft type, and nothing that names one can be tested on a
 * bare JVM</b> -- the class does not even load, so the whole suite dies before the first
 * assertion. Every number the gold enchantments produce is therefore decided in this file and
 * handed to the handler, which is the only place allowed to know what an ingot is. The tables are
 * the interesting part and they are the part that can be asserted.</p>
 *
 * <h2>Why a {@link Random} and not vanilla's</h2>
 *
 * <p>{@code RandomSource} is a Minecraft interface, so a test could not feed one. The handler
 * bridges it by seeding a fresh {@link Random} from the server's own source per roll, which costs
 * one object per drop table and keeps the arithmetic pure.</p>
 */
public final class GoldEnchantRules {

    /**
     * The extra damage a marked enemy takes. <b>Never applied twice:</b> the mark is one effect and
     * the multiplier is read from the effect, so refreshing the mark refreshes only the timer.
     */
    public static final float DEATH_MARK_MULTIPLIER = 1.2F;

    /** Life steal per level: five percent of the damage actually dealt. */
    public static final float LIFE_STEAL_PER_LEVEL = 0.05F;

    /** Wind, movement side: five percent per level, and it stops at five. */
    public static final float WIND_MOVEMENT_PER_LEVEL = 0.05F;
    public static final int WIND_MOVEMENT_MAX_LEVELS = 5;

    /** Feather weight: gold is quick but soft, so haste is bought with a slower swing. */
    public static final float FEATHER_ATTACK_SPEED_PER_LEVEL = -0.10F;
    public static final int FEATHER_MAX_LEVELS = 3;

    /** Phantom strike, per level: the chance the blow goes through armour completely. */
    public static final float PHANTOM_PIERCE_PER_LEVEL = 0.15F;

    /** Midas on a tool: the chance a mined ore comes out gold instead of itself. */
    public static final float TOOL_MIDAS_CHANCE_L1 = 0.25F;
    public static final float TOOL_MIDAS_CHANCE_L2 = 0.50F;
    public static final float TOOL_MIDAS_CHANCE_L3 = 0.75F;

    /** Midas on a tool, third level only: the extra nugget that comes with the coin. */
    public static final int TOOL_MIDAS_NUGGETS_L3 = 1;

    /** The second drop, ten percent of the time, on a golden tool. */
    public static final float TOOL_DOUBLE_DROP_CHANCE = 0.10F;

    private GoldEnchantRules() {
    }

    /** Clamps a level to the enchantment's own ceiling, so an out-of-range stack cannot inflate it. */
    public static int clampLevel(int level, int maxLevel) {
        return Math.max(0, Math.min(maxLevel, level));
    }

    // ------------------------------------------------------------------ midas, weapon

    /**
     * Ingots from a kill at level two and three. The roll happens here; the item is vanilla's.
     *
     * <p>Level two and three roll <b>one to two</b> each at fifty percent, which means a level two
     * kill drops an ingot half the time and two when it does. A level one kill drops none: the
     * nuggets are the level's own reward, and an ingot at level one would make the enchantment pay
     * for itself on the first kill.</p>
     */
    public static int midasIngots(int level, Random random) {
        if (level < 2) {
            return 0;
        }
        int count = 0;
        if (random.nextFloat() < 0.50F) {
            count++;
        }
        if (random.nextFloat() < 0.50F) {
            count++;
        }
        return count;
    }

    /**
     * Nuggets from a kill. <b>Every level drops these, always:</b> two to four, which is what makes
     * the enchantment legible -- a player who reads "gold on kill" sees gold on kill, and the ingots
     * are the upgrade rather than the whole feature.
     */
    public static int midasNuggets(int level, Random random) {
        if (level < 1) {
            return 0;
        }
        return 2 + random.nextInt(3);
    }

    /** A golden apple, at the third level, one in ten. */
    public static boolean midasGoldenApple(int level, Random random) {
        return level >= 3 && random.nextFloat() < 0.10F;
    }

    /** Golden carrots, at the third level: one to three, one in five. */
    public static int midasGoldenCarrots(int level, Random random) {
        if (level < 3 || random.nextFloat() >= 0.20F) {
            return 0;
        }
        return 1 + random.nextInt(3);
    }

    /** An enchanted apple, at the third level, one in fifty. The reason to keep a gold weapon. */
    public static boolean midasEnchantedApple(int level, Random random) {
        return level >= 3 && random.nextFloat() < 0.02F;
    }

    // ------------------------------------------------------------------ midas, tool

    /** The chance a mined ore block's drop comes out gold. */
    public static float toolMidasChance(int level) {
        return switch (clampLevel(level, 3)) {
            case 1 -> TOOL_MIDAS_CHANCE_L1;
            case 2 -> TOOL_MIDAS_CHANCE_L2;
            case 3 -> TOOL_MIDAS_CHANCE_L3;
            default -> 0.0F;
        };
    }

    /** Whether this roll turned the drop to gold. */
    public static boolean toolMidasTurned(int level, Random random) {
        return random.nextFloat() < toolMidasChance(level);
    }

    /** The extra nugget the third level adds to a converted drop. */
    public static int toolMidasBonusNuggets(int level) {
        return clampLevel(level, 3) >= 3 ? TOOL_MIDAS_NUGGETS_L3 : 0;
    }

    /** Whether the block drops twice. One level only, ten percent, no scaling. */
    public static boolean toolDropsTwice(int level, Random random) {
        return clampLevel(level, 1) >= 1 && random.nextFloat() < TOOL_DOUBLE_DROP_CHANCE;
    }

    // ------------------------------------------------------------------ the others

    /**
     * Health restored by life steal: five percent of the damage that was actually dealt, per level.
     *
     * @param damage the post-armour amount, which is the number the target lost and the only one
     *               that may be turned into healing
     */
    public static float lifeStealHeal(float damage, int level) {
        int l = clampLevel(level, 3);
        if (l <= 0 || damage <= 0.0F) {
            return 0.0F;
        }
        return damage * LIFE_STEAL_PER_LEVEL * l;
    }

    /** Wind's movement bonus, and it stops at five levels because twenty five percent is a horse. */
    public static float windMovementBonus(int level) {
        int l = clampLevel(level, WIND_MOVEMENT_MAX_LEVELS);
        return l <= 0 ? 0.0F : WIND_MOVEMENT_PER_LEVEL * l;
    }

    /**
     * Feather weight's attack speed offset, as the item system expects it.
     *
     * <p><b>An offset and not a total.</b> {@code SwordItem.createAttributes} takes the difference
     * between the speed it wants and vanilla's 4.0, and the mixin that reads this number does the
     * same, so a level of feather weight is minus a tenth of a swing on top of the weapon's own.</p>
     */
    public static float featherAttackSpeedOffset(int level) {
        int l = clampLevel(level, FEATHER_MAX_LEVELS);
        return l <= 0 ? 0.0F : FEATHER_ATTACK_SPEED_PER_LEVEL * l;
    }

    /** Whether this blow ignores armour. */
    public static boolean phantomPiercesArmour(int level, Random random) {
        int l = clampLevel(level, 2);
        return l > 0 && random.nextFloat() < PHANTOM_PIERCE_PER_LEVEL * l;
    }

    /** Wind's dig speed bonus on a tool: ten percent per level, capped at three. */
    public static float windDigSpeedBonus(int level) {
        return 0.10F * clampLevel(level, 3);
    }

    /**
     * The whole drop table in one call, in the order a log should read it.
     *
     * <p><b>One roll set per call, and the order is the table's,</b> not alphabetical: the numbers
     * are drawn once each here so a test can assert the shape of a kill without running the event.</p>
     */
    public static List<String> midasWeaponTable(int level, Random random) {
        List<String> out = new ArrayList<>();
        int nuggets = midasNuggets(level, random);
        if (nuggets > 0) {
            out.add("gold_nugget x" + nuggets);
        }
        int ingots = midasIngots(level, random);
        if (ingots > 0) {
            out.add("gold_ingot x" + ingots);
        }
        if (midasGoldenApple(level, random)) {
            out.add("golden_apple");
        }
        int carrots = midasGoldenCarrots(level, random);
        if (carrots > 0) {
            out.add("golden_carrot x" + carrots);
        }
        if (midasEnchantedApple(level, random)) {
            out.add("enchanted_golden_apple");
        }
        return out;
    }
}
