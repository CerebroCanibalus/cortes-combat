package com.cortes.combat.item;

import com.cortes.combat.core.weapon.GuardProfile;
import com.cortes.combat.core.weapon.SpecialType;
import com.cortes.combat.core.weapon.WeaponMaterial;
import com.cortes.combat.core.weapon.WeaponProfile;
import com.cortes.combat.core.weapon.WeaponShape;

import java.util.EnumSet;
import java.util.Set;

/**
 * One of the five weapon families, as the single place a weapon is decided.
 *
 * <p>This enum is the catalogue. Thirty items exist because five families times an uneven set of
 * materials exist, and the unevenness is the design: the dagger has a wooden one because it is the
 * access weapon, the warhammer has no wooden or stone one because it is the commitment weapon, and
 * the other three skip wood because a blade with no tier reads as a stick. DESIGN.md 5bis.</p>
 *
 * <p><b>The whole asymmetry is expressed as two sets, not as thirty registrations.</b> That is
 * what makes the count a test instead of an agreement: if a material moves from one set to
 * another, the catalogue changes by one line and {@code WeaponCatalogueTest} re-counts it.</p>
 *
 * <p>The {@code *} ingredient is per family and not per material, because both families that use
 * it have a rule rather than a list. The ropera's extra is a nugget on every metal, a flint on
 * stone and a gold ingot on netherite, and that rule is the reason the ropera recipe is written
 * by hand rather than coming out of a tag. The warhammer's extra is an iron ingot everywhere and
 * gold on netherite.</p>
 */
public enum WeaponFamily {

    /**
     * The ropera: the narrowest cone in the game, the hardest single hit among the light weapons,
     * and the fastest thing that can still hurt.
     *
     * <p>No charge and no right click. Its answer to a fight is precision, and precision is a
     * thing you either have or do not; a window that fills up while you aim at an exact spot is
     * a second thing to manage, and the design says no.</p>
     */
    ROPERA("ropera", "Ropera", WeaponProfile.ROPERA, WeaponShape.ROPERA,
            4.0F, 2.0F, SpecialType.NONE),

    /**
     * The halberd: the longest reach and the widest cone of the four that are not the sabre.
     *
     * <p>Its right click is the multi-hit, and the shape is the most expensive grid in the mod on
     * purpose -- four material pieces and three sticks for the weapon that covers a crowd.</p>
     */
    ALABARDA("alabarda", "Alabarda", WeaponProfile.HALBERD, WeaponShape.ALABARDA,
            4.0F, 1.1F, SpecialType.MULTI_HIT),

    /**
     * The warhammer: the slowest thing in the game and the lowest base damage, and the only weapon
     * that escalates.
     *
     * <p>Slow on purpose. The escalation pays out over a four second window of consecutive hits,
     * and a weapon fast enough to leave that window is a weapon that never escalates. Its ceiling
     * is the highest in the set and its floor is the lowest, which is the whole trade.</p>
     */
    MARTILLO("martillo", "Martillo de Guerra", WeaponProfile.WARHAMMER, WeaponShape.MARTILLO,
            3.0F, 0.9F, SpecialType.NONE),

    /**
     * The dagger: the fastest thing in the game and the widest cone on a short weapon.
     *
     * <p>The only one of the five with a wooden version, and the only one of the five that can be
     * made from a tree on the first day.</p>
     */
    DAGA("daga", "Daga", WeaponProfile.DAGGER, WeaponShape.DAGA,
            2.0F, 2.6F, SpecialType.NONE),

    /**
     * The shotel: the widest cone in the game, the shortest reach that is not the warhammer's,
     * and a parry.
     *
     * <p>The only weapon whose full charge doubles rather than adds, which is why its charge
     * window is the shortest in the set: a bigger number needs less time to be a decision.</p>
     */
    SHOTEL("shotel", "Shotel", WeaponProfile.SABRE, WeaponShape.SHOTEL,
            3.0F, 2.0F, SpecialType.PARRY);

    /**
     * Every player starts with this much ATTACK_SPEED, and a weapon's attribute is an offset from
     * it rather than a total.
     *
     * <p>Four, verified in 1.21.1: vanilla's sword is
     * {@code SwordItem.createAttributes(tier, 3, -2.4F)} and lands on 1.6, which is four minus two
     * point four. It is written down here because the alternative is a literal 4.0 in two places
     * with nothing saying what it is, and getting the sign wrong makes thirty weapons twice as
     * fast as written with a green build.</p>
     */
    public static final float PLAYER_BASE_ATTACK_SPEED = 4.0F;

    private final String id;

    private final String displayName;
    private final WeaponProfile profile;
    private final WeaponShape shape;
    private final float attackDamage;
    private final float attackSpeed;
    private final SpecialType special;

    private final Set<WeaponMaterial> materials;

    WeaponFamily(String id, String displayName, WeaponProfile profile, WeaponShape shape,
                 float attackDamage, float attackSpeed, SpecialType special) {
        this.id = id;
        this.displayName = displayName;
        this.profile = profile;
        this.shape = shape;
        this.attackDamage = attackDamage;
        this.attackSpeed = attackSpeed;
        this.special = special;
        this.materials = materialsFor(profile);
    }

    /**
     * What the family's attack speed is as an attribute offset, not a total.
     *
     * <p>The three lines this exists for. Vanilla's sword is built with
     * {@code createAttributes(tier, 3, -2.4F)} and ends up at 1.6, because a player's base
     * ATTACK_SPEED is four and the argument is added to it. The catalogue stores the total, because
     * that is the number a design reads, and handing the total to an API that wants the offset
     * makes every weapon four plus too fast -- a ropera at six, which is three ticks between
     * swings.</p>
     *
     * <p>It lives here, next to both numbers, because the arithmetic has to be somewhere a test
     * can reach. The previous test read {@code family.attackSpeed()} and asserted the interval it
     * implied, which was the declared number and therefore always agreed with itself while every
     * weapon in the game was running at double tempo.</p>
     */
    public float attackSpeedOffset() {
        return attackSpeed - PLAYER_BASE_ATTACK_SPEED;
    }

    /**
     * The ticks between swings this family actually gets, which is the design table.
     *
     * <p>Pure, so the five numbers the whole catalogue hangs on are a test rather than an
     * agreement.</p>
     */
    public int swingInterval() {
        return com.cortes.combat.attack.AttackTiming.ticksFor(attackSpeed);
    }

    /** The registry suffix. Also the texture suffix, so the two cannot drift apart. */
    public String id() {
        return id;
    }

    /**
     * How much guard this family is worth, if any.
     *
     * <p><b>Stated here because the Java class cannot say it.</b> Every one of these items is a
     * {@code SwordItem} -- that is how it gets vanilla's own {@code createAttributes} and therefore
     * the right damage and attack speed per tier -- and {@code GuardStats} used to answer "can this
     * guard" with {@code instanceof SwordItem}. So all thirty of them guarded, including the two
     * whose right button is a special, and nothing anywhere arbitrated between the two meanings of
     * one button.</p>
     *
     * <p>The component is the right layer for it, because the component layer already existed in
     * {@code GuardStats} and the vanilla class check is the fallback it was meant to be.</p>
     *
     * <p>Two different reasons for saying no, and both worth writing down because neither one
     * generalises:</p>
     * <ul>
     *   <li><b>The dagger and the sabre have a special on the right button.</b> The special wins.
     *       A button cannot mean two things and the design says the special is what the right
     *       button is for.</li>
     *   <li><b>The dagger could not guard even without that.</b> Blocking with the fastest weapon
     *       is strictly better than blocking with the sword -- same guard, faster attacks -- which
     *       is the same reason a shield in the off hand would dissolve the unified guard.</li>
     *   <li><b>The warhammer has no special and still cannot guard.</b> It is the commitment
     *       weapon: slow, escalating, highest ceiling and lowest floor. If it also blocked for free
     *       there would be no commitment left in it.</li>
     * </ul>
     *
     * <p><b>None of the five guard.</b> The ropera was left guarding here on the reasoning that
     * it has no special and is the sword-adjacent one, and the playtest settled it: "the ropera
     * can guard, which it should not". The guard is a shield or a sword, and none of the five new
     * families is either -- they are a ropera, an alabarda, a warhammer, a dagger and a sabre. The
     * ropera's whole answer to a fight is precision rather than absorbing.</p>
     *
     * <p>That leaves the guard where the design puts it: the sword family and vanilla shields, and
     * it is the cleaner answer besides, because "which weapons guard" stops being a list with
     * exceptions and becomes "the two things a guard is made of".</p>
     */
    public GuardProfile guard() {
        // One answer for all five, and returning it unconditionally rather than switching over the
        // constants is deliberate: a family added later inherits the right behaviour, and a switch
        // would need a new arm saying so.
        return GuardProfile.NONE;
    }

    /**
     * The bare name a player reads, in Spanish.
     *
     * <p>The material is not in here: it is added by {@link #displayName(WeaponMaterial)} so the
     * two halves are composed in one place and a name never ends up as "de Hierro" with the
     * weapon missing.</p>
     */
    public String displayName() {
        return displayName;
    }

    /** The full name, as the hotbar shows it: {@code Ropera de Hierro}. */
    public String displayName(WeaponMaterial material) {
        return displayName + " de " + material.displayName();
    }

    public WeaponProfile profile() {
        return profile;
    }

    public WeaponShape shape() {
        return shape;
    }

    /** The weapon's own damage, before the material's bonus and the player's own. */
    public float attackDamage() {
        return attackDamage;
    }

    /**
     * Attacks per second, which is vanilla's own unit for it.
     *
     * <p>This is the attribute the hard cooldown reads, so it is the tempo the player feels. The
     * dagger at 2.6 is a swing every eight ticks and the warhammer at 0.9 is one every twenty
     * two, and that gap is the single largest number in the catalogue.</p>
     */
    public float attackSpeed() {
        return attackSpeed;
    }

    public SpecialType special() {
        return special;
    }

    public boolean hasSpecial() {
        return special.isPresent();
    }

    /** Which materials this family exists in. Uneven, and that is the design. */
    public Set<WeaponMaterial> materials() {
        return materials;
    }

    public boolean existsIn(WeaponMaterial material) {
        return materials.contains(material);
    }

    /**
     * An ingredient as a name plus whether that name is a tag.
     *
     * <p>Pure on purpose: {@code Ingredient} is a Minecraft type and would drag the game onto the
     * classpath of anything holding it, which is the mistake this project's other enums have
     * already made once. The datagen turns this into a real {@code Ingredient} and nothing else
     * has to care.</p>
     */
    public record IngredientRef(String id, boolean tag) {

        public static IngredientRef item(String id) {
            return new IngredientRef(id, false);
        }

        public static IngredientRef tag(String id) {
            return new IngredientRef(id, true);
        }
    }

    /**
     * The tag the ropera's metal tip reads.
     *
     * <p>Ours, because the tip is our own thing: copper nuggets on copper, iron nuggets on iron.
     * The tag provider adds the members. Reading a tag rather than naming an item is what lets a
     * datapack that adds a metal have its metal tipped too.</p>
     */
    public static final String ROPERA_TIP_TAG = "cortescombat:ropera_tips";

    /** The body ingredient: anything in the material's tool-material tag. */
    public IngredientRef bodyIngredient(WeaponMaterial material) {
        return IngredientRef.tag(material.repairTag());
    }

    /**
     * What goes in the {@code *} cell, or {@code null} when this family has no such cell.
     *
     * <p>Both families that have one have a <b>rule</b> rather than a list, and writing the rule
     * down instead of the eleven answers is what makes it checkable:</p>
     *
     * <ul>
     *   <li>The ropera's tip is a nugget on every metal, a flint on stone and a gold ingot on
     *       netherite. It is not the tier's own material, and that is the whole reason these six
     *       recipes are written by hand instead of falling out of a tag: a grid whose two metal
     *       cells want different things cannot be a tag.</li>
     *   <li>The warhammer's core is an iron ingot on every tier except netherite, which takes
     *       gold, because the head has to be one piece denser than the haft.</li>
     * </ul>
     *
     * <p>The diamond tier is the interesting case in both and is deliberate: a diamond ropera is
     * tipped with an emerald and a diamond warhammer is cored with iron, because the rule is about
     * the shape of the object and not about spending the most expensive thing in the game on a
     * decoration.</p>
     */
    public IngredientRef extraIngredient(WeaponMaterial material) {
        if (!shape.needsExtraIngredient()) {
            return null;
        }
        if (profile == WeaponProfile.ROPERA) {
            return switch (material) {
                case STONE -> IngredientRef.item("minecraft:flint");
                case NETHERITE -> IngredientRef.item("minecraft:gold_ingot");
                case DIAMOND -> IngredientRef.item("minecraft:emerald");
                case WOODEN, COPPER, IRON, GOLDEN -> IngredientRef.tag(ROPERA_TIP_TAG);
            };
        }
        if (profile == WeaponProfile.WARHAMMER) {
            return material == WeaponMaterial.NETHERITE
                    ? IngredientRef.item("minecraft:gold_ingot")
                    : IngredientRef.item("minecraft:iron_ingot");
        }
        throw new IllegalStateException(shape.id() + " has a '*' cell and no rule for it");
    }

    /**
     * The registry name of one item, as {@code <material>_<family>}.
     *
     * <p>The same string is the item id, the model name and the texture name. Computing it once
     * here is what stops the three from drifting, and the texture rename from {@code rapier} to
     * {@code ropera} was thirty renames for exactly the reason this method exists.</p>
     */
    public String itemName(WeaponMaterial material) {
        if (!existsIn(material)) {
            throw new IllegalArgumentException(
                    "no " + id + " in " + material.id() + ": " + this + " exists in " + materials);
        }
        return material.id() + "_" + id;
    }

    /**
     * Which materials each family is cut in.
     *
     * <p>Written as a question per material rather than as a list per family, because the two
     * exceptions are both "and not this one". The dagger has no exception, the ropera and the
     * halberd and the shotel skip wood, and the warhammer skips wood and stone.</p>
     */
    private static Set<WeaponMaterial> materialsFor(WeaponProfile profile) {
        Set<WeaponMaterial> set = EnumSet.noneOf(WeaponMaterial.class);
        for (WeaponMaterial material : WeaponMaterial.values()) {
            switch (profile) {
                case ROPERA, HALBERD, SABRE -> {
                    if (material.isRapierMaterial()) {
                        set.add(material);
                    }
                }
                case WARHAMMER -> {
                    if (material.isWarhammerMaterial()) {
                        set.add(material);
                    }
                }
                default -> set.add(material);
            }
        }
        return set;
    }

    /** Every item this family contributes, material by material, in progression order. */
    public Iterable<WeaponMaterial> materialsInOrder() {
        return materials.stream().sorted((a, b) -> Integer.compare(a.rank(), b.rank())).toList();
    }

    /**
     * Every item name in the whole catalogue, in family order then progression order.
     *
     * <p>Pure, so the datagen providers and the tests read the same list instead of each keeping
     * their own. It lives here rather than on a provider for a reason that cost a compile error:
     * a static on a class that implements {@code DataProvider} cannot even be <em>read</em> from a
     * bare-JVM test, because the test classpath has no Minecraft in it. Any list a test needs has
     * to live below the Minecraft line.</p>
     */
    public static Set<String> allItemNames() {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (WeaponFamily family : values()) {
            for (WeaponMaterial material : family.materialsInOrder()) {
                names.add(family.itemName(material));
            }
        }
        return names;
    }
}
