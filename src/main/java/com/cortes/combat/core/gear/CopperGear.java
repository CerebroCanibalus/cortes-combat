package com.cortes.combat.core.gear;

import java.util.List;

/**
 * The copper set's names and numbers, below the Minecraft line.
 *
 * <p><b>Copper is the material vanilla 1.21.1 does not ship</b> (it arrives with the copper age),
 * so the whole gear set -- the five tools and the four armour pieces -- is ours. Its identity comes
 * from {@code DESIGN.md 5bis}: <em>copper lasts and does not hit</em>. The durability lives in
 * {@link com.cortes.combat.core.weapon.WeaponMaterial#COPPER} and the low damage in
 * {@link com.cortes.combat.registry.CortesTier#getAttackDamageBonus}; what is here is the part that
 * belongs to the gear and to nothing else.</p>
 *
 * <p>Pure on purpose: a test reads these numbers and the two registries read them too, and a list
 * a test needs cannot name a Minecraft type or it stops compiling before it can assert anything.</p>
 */
public final class CopperGear {

    /**
     * The tools' mining speed, and it is the one number in the set that vanilla has no equivalent
     * for.
     *
     * <p>Five, between stone's four and iron's six, which is exactly where the material sits: the
     * tier is "between stone and iron" and the speed is the only stat that can say so. Copper's
     * mining <em>level</em> is stone's -- it mines what a stone pickaxe mines and not what an iron
     * one does -- so the speed is the whole of the upgrade and the durability is the rest of it.</p>
     */
    public static final float MINING_SPEED = 5.0F;

    public static final String SWORD = "copper_sword";
    public static final String PICKAXE = "copper_pickaxe";
    public static final String AXE = "copper_axe";
    public static final String SHOVEL = "copper_shovel";
    public static final String HOE = "copper_hoe";

    public static final String HELMET = "copper_helmet";
    public static final String CHESTPLATE = "copper_chestplate";
    public static final String LEGGINGS = "copper_leggings";
    public static final String BOOTS = "copper_boots";

    /**
     * The armour's defence, slot by slot, and it sits <b>above iron</b> rather than below it.
     *
     * <p>Iron is 2/6/5/2 for fifteen and chainmail is 2/5/4/1 for twelve; copper is 3/6/5/3 for
     * seventeen, which is a real step up from both and still under diamond's twenty. The design's
     * call, and it is the opposite of the tools' arrangement on purpose: the tools are cheap and
     * between stone and iron, the armour is the one piece of copper gear worth making.</p>
     */
    public static final int HELMET_DEFENSE = 3;
    public static final int CHESTPLATE_DEFENSE = 6;
    public static final int LEGGINGS_DEFENSE = 5;
    public static final int BOOTS_DEFENSE = 3;

    public static final int TOTAL_DEFENSE =
            HELMET_DEFENSE + CHESTPLATE_DEFENSE + LEGGINGS_DEFENSE + BOOTS_DEFENSE;

    /**
     * The armour's durability multiplier, and it is <b>less than half of iron's fifteen</b>.
     *
     * <p>Vanilla computes a piece's uses as {@code Type.getDurability(multiplier)}: helmet eleven,
     * chest sixteen, leggings fifteen, boots thirteen times this number. At eight a full suit is
     * 440 against iron's 825 -- so the armour is the best protection in its weight class and pays
     * for it by being the shortest lived, which is the trade the design asked for by name.</p>
     *
     * <p><b>And it is the opposite of the tools' durability on purpose.</b> A copper pickaxe lasts
     * longer than an iron one; copper armour does not. The tool is a commitment, the armour is a
     * burst.</p>
     */
    public static final int ARMOR_DURABILITY_MULTIPLIER = 8;

    public static final List<String> TOOL_NAMES = List.of(SWORD, PICKAXE, AXE, SHOVEL, HOE);

    public static final List<String> ARMOR_NAMES = List.of(HELMET, CHESTPLATE, LEGGINGS, BOOTS);

    /** Every name this set adds to the item registry, tools first. */
    public static final List<String> ALL_NAMES = allNames();

    private static List<String> allNames() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>(TOOL_NAMES);
        names.addAll(ARMOR_NAMES);
        return List.copyOf(names);
    }

    private CopperGear() {
    }
}
