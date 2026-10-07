package com.cortes.combat.core.weapon;

import java.util.Locale;

/**
 * The seven materials this mod cuts weapons out of, as pure data.
 *
 * <p>Deliberately <b>not</b> an implementation of vanilla's {@code Tier}. That interface returns
 * a {@code TagKey<Block>} and an {@code Ingredient}, both Minecraft types, so an enum holding one
 * could not be class-loaded by a unit test on a bare JVM -- and this enum is exactly the kind of
 * thing that wants a test, because the whole catalogue is generated from it. A tier that quietly
 * lost its repair material would ship thirty items that cannot be repaired, and nothing in the
 * game would say so.</p>
 *
 * <p>So identity lives here, numbers that need Minecraft live in {@link CortesTier}, and the glue
 * between them is one method instead of a second list to keep in sync.</p>
 *
 * <h2>The copper question</h2>
 *
 * <p>Copper is Mojang's from 1.21.9 on, and we ship our own (DESIGN.md 5bis). Seven entries where
 * vanilla has six. When the version that brings copper age arrives this enum loses one line and
 * the recipes keep working, because they read the material tag rather than a named item.</p>
 *
 * <p><b>The uses and enchantment numbers are Mojang's for the six vanilla tiers, copied from the
 * 1.21.1 sources so a player repairing an item at an anvil gets the number they expect.</b> They
 * were copied rather than invented because there is no design reason for our iron to survive a
 * different number of hits than vanilla's iron.</p>
 *
 * <h2>And the two that are not Mojang's, on purpose</h2>
 *
 * <p><b>Gold and copper, and the playtest asked for both.</b> Vanilla's gold is thirty two uses,
 * which is a joke with a durability bar attached: a golden sword breaks in the time it takes to
 * notice it works. The design's gold is <em>between stone and iron</em>, which is where a fragile
 * material with the best enchantability in the game belongs -- it still dies first of the metals,
 * but it dies after a fight instead of during one.</p>
 *
 * <p>Copper goes <em>above</em> iron, and the price is on its damage: see
 * {@code CortesTier#getAttackDamageBonus}, where copper sits below stone. The identity that falls
 * out is legible -- <b>copper lasts and does not hit</b> -- and it is the one arrangement where
 * the warhammer's first tier is a commitment rather than a free upgrade.</p>
 */
public enum WeaponMaterial {

    /** The access tier. Only the dagger has one; the other four start above it. */
    WOODEN("wooden", 0, 59, 15, "wooden_tool_materials"),

    STONE("stone", 1, 131, 5, "stone_tool_materials"),

    /**
     * Ours, and above iron rather than between it and stone.
     *
     * <p>Three hundred and twenty against iron's two hundred and fifty. It pays for that with the
     * lowest damage bonus in the set -- below stone -- so the tier reads as "the durable one" and
     * not as a free step up. See the class comment.</p>
     */
    COPPER("copper", 2, 320, 13, "copper_tool_materials"),

    IRON("iron", 3, 250, 14, "iron_tool_materials"),

    /**
     * Between stone and iron, which vanilla never was.
     *
     * <p>One hundred and ninety against vanilla's thirty two, and the enchantability stays at
     * Mojang's twenty two -- the highest of any tier. <b>Gold is the material you enchant, not the
     * one you fight with</b>, and a material with the best enchantability in the game cannot also
     * break before the enchantment pays for itself.</p>
     */
    GOLDEN("golden", 4, 190, 22, "gold_tool_materials"),

    DIAMOND("diamond", 5, 1561, 10, "diamond_tool_materials"),
    NETHERITE("netherite", 6, 2031, 15, "netherite_tool_materials");

    private final String id;
    private final int rank;
    private final int uses;
    private final int enchantmentValue;
    private final String repairTag;

    WeaponMaterial(String id, int rank, int uses, int enchantmentValue, String repairTag) {
        this.id = id;
        this.rank = rank;
        this.uses = uses;
        this.enchantmentValue = enchantmentValue;
        this.repairTag = repairTag;
    }

    /** The registry suffix. Also the texture suffix, so the two cannot drift apart. */
    public String id() {
        return id;
    }

    /** Where this material sits in the progression. */
    public int rank() {
        return rank;
    }

    public int uses() {
        return uses;
    }

    public int enchantmentValue() {
        return enchantmentValue;
    }

    /**
     * The vanilla tool-material tag, without a namespace.
     *
     * <p>Recipes read this rather than a named item, so a datapack that adds a material to a
     * vanilla tag has its material accepted by our recipes for free. Copper is the exception and
     * needs its own tag, generated alongside the recipes.</p>
     */
    public String repairTag() {
        return repairTag;
    }

    /** Whether the ropera's second ingredient is a nugget rather than the tier's own edge. */
    public boolean isMetal() {
        return this != WOODEN && this != STONE;
    }

    /**
     * Whether the warhammer exists in this material.
     *
     * <p>A rank rather than a list of the three that qualify, so adding a material later cannot
     * leave the warhammer out by omission.</p>
     */
    public boolean isWarhammerMaterial() {
        return rank >= COPPER.rank;
    }

    /** Every material but stone, which the warhammer skips because it is the beginner's tier. */
    public boolean isRapierMaterial() {
        return this != WOODEN;
    }

    /**
     * The same name in English, for the {@code en_us} file.
     *
     * <p><b>A switch and not a lookup table, and the two are not interchangeable.</b> The
     * Spanish one is a noun and goes in front of the weapon ("Ropera de Hierro"), because that is
     * how Spanish builds it; English puts the material first and needs an adjective ("Iron
     * Ropera"). So the English string cannot be assembled from the Spanish one by moving a
     * space, and a table shared between the two languages would have to encode the grammar
     * difference as data.</p>
     *
     * <p>{@code Netherite} is the same word in both, and that is not an oversight: it is Mojang's
     * own spelling and inventing an English spelling for it would be worse than borrowing it.</p>
     */
    public String displayNameEn() {
        return switch (this) {
            case WOODEN -> "Wooden";
            case STONE -> "Stone";
            case COPPER -> "Copper";
            case IRON -> "Iron";
            case GOLDEN -> "Golden";
            case DIAMOND -> "Diamond";
            case NETHERITE -> "Netherite";
        };
    }

    /**
     * The name a player reads, in Spanish, because that is the language this mod is written in.
     *
     * <p>Separate from {@link #id()} on purpose. The id is a registry name: it ends up in every
     * datapack, every recipe filename and every world save, and it is what appears in a bug
     * report. The display name is what a player reads in the hotbar and it is translated. The two
     * being the same string is a coincidence of English, and the moment either is translated they
     * stop being interchangeable.</p>
     */
    public String displayName() {
        return switch (this) {
            case WOODEN -> "Madera";
            case STONE -> "Piedra";
            case COPPER -> "Cobre";
            case IRON -> "Hierro";
            case GOLDEN -> "Oro";
            case DIAMOND -> "Diamante";
            case NETHERITE -> "Netherite";
        };
    }

    public static WeaponMaterial byId(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        for (WeaponMaterial material : values()) {
            if (material.id.equals(key)) {
                return material;
            }
        }
        throw new IllegalArgumentException("Unknown material: " + id);
    }
}
