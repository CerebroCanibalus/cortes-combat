package com.cortes.combat.core.weapon;

/**
 * What kind of weapon this is, as the blue line vanilla shows under an item's name.
 *
 * <h2>Why vanilla's own category line was missing, and why ours is wider</h2>
 *
 * <p>Vanilla prints one for food ("Food"), for a potion, for a spawn egg -- and <b>not for a
 * weapon</b>, because for vanilla a weapon's category is the line "When in Main Hand:" and its two
 * attribute numbers. This mod replaces those two numbers with seven lines, so the one piece of
 * context they carried -- <em>what am I looking at</em> -- went with them, and a player comparing a
 * ropera against a shotel was reading two identical blocks of numbers.</p>
 *
 * <p><b>And the split the playtest asked for is real, because this catalogue is not one kind of
 * weapon.</b> Vanilla has four melee shapes and they differ by a couple of points of damage; this
 * game has a two and a quarter block sabre and a five block halberd, a four tick dagger and a
 * thirty three tick maza. Those are different <em>roles</em>, and a category line is the cheapest
 * possible place to say so.</p>
 *
 * <h2>By capacity and not by name</h2>
 *
 * <p>Every rule below reads a number the weapon already carries -- its cone, its interval, whether
 * it has a second cone for the right button -- so a weapon added tomorrow classifies itself. A list
 * of names would be a second catalogue, and the two would disagree the first time somebody retuned
 * a reach without remembering this file.</p>
 *
 * <p><b>The order of the questions is the priority, and it is deliberate.</b> A weapon has exactly
 * one category, and the halberd is both long and heavy: it answers "long" because reach is what
 * decides how you fight with it and the interval is what it costs you.</p>
 */
public enum WeaponCategory {

    /** It has a second cone for the right button: the arcabuz. */
    RANGED("ranged"),

    /**
     * A tool rather than a weapon: the pickaxe, and the pala which fights anyway.
     *
     * <p><b>Asked before reach and interval, and that is the whole point.</b> The pala's three
     * blocks and twenty tick swing would land it in medium or heavy by the numbers, and both of
     * those are claims about how a <em>weapon</em> is used. A tool that happens to fight is still
     * the thing a player reaches for to dig, and the category is the one line that says so.</p>
     *
     * <p>The axe is deliberately not here: it is a weapon that can also chop, which is the other
     * side of the same distinction, and the numbers already place it as heavy.</p>
     */
    TOOL("tool"),

    /** Reaches five blocks or more: the trident and the alabarda. */
    LONG("long"),

    /** Nineteen ticks or more between swings, and not long: the hacha, the maza, the martillo. */
    HEAVY("heavy"),

    /** Under three blocks: the daga and the sable. */
    SHORT("short"),

    /** Everything else: the espada, the ropera, the azada, the pala. */
    MEDIUM("medium"),

    /** The fists. No item, and still a weapon in this design. */
    NATURAL("natural");

    /**
     * Where "long" starts, in blocks.
     *
     * <p>Five, and it is the gap in the table rather than a round number: the two longest melee
     * weapons in the game are the halberd at five and the trident at five point three, and the next
     * one down is the espada at three point six. Nothing sits between them, so the boundary is a
     * decision with no wrong answer -- and five is the side of it that leaves the espada a medium
     * weapon instead of a long one.</p>
     */
    public static final float LONG_REACH = 5.0F;

    /**
     * Where "short" ends, in blocks.
     *
     * <p>Three, and it is where the daga's two point nine and the sable's two point two five sit
     * against the pala's three point oh. <b>The boundary is exactly the pala's own reach</b>, so the
     * shortest of the ordinary weapons is the first of the medium ones and the two blades built for
     * being close are the only short weapons in the game.</p>
     */
    public static final float SHORT_REACH = 3.0F;

    /**
     * Where "heavy" starts, in ticks between swings.
     *
     * <p>Nineteen, which is the warhammer's own interval. The hacha's twenty four and the maza's
     * thirty seven are the other two, and they are heavy in the sense the word is for: <b>a swing
     * you have to commit to before you know whether it lands.</b></p>
     */
    public static final int HEAVY_TICKS = 19;

    /**
     * And where it starts in reach, which the playtest is what forced.
     *
     * <p><b>The interval alone does not separate heavy from medium, and the pala is why.</b>
     * Vanilla's shovel swings every twenty ticks -- <em>slower than the warhammer's nineteen</em> --
     * so a rule reading only the clock called the pala a heavy weapon and the warhammer a medium
     * one, which is backwards in both directions at once. The pala is a tool that fights; the
     * warhammer is a weapon that happens to be slow.</p>
     *
     * <p>So heavy asks two questions and both have to be yes: <b>slow, and long enough to be swung
     * rather than held.</b> Three point four is the warhammer's, the hacha's and the maza's own
     * reach, and it is above the pala's three and the azada's three point two -- which is the whole
     * of the separation, and it is a real one rather than a fudge: a weapon you swing from further
     * out is one you commit to, and a tool you use at arm's length is not.</p>
     */
    public static final float HEAVY_REACH = 3.4F;

    private final String id;

    WeaponCategory(String id) {
        this.id = id;
    }

    /** The translation key suffix. */
    public String id() {
        return id;
    }

    /**
     * Which category a resolved weapon is.
     *
     * @param profile      the weapon's profile
     * @param reach        its resolved cone
     * @param intervalTicks ticks between swings, after the tempo
     * @param hasShotCone  whether its right button has a cone of its own
     */
    public static WeaponCategory forWeapon(WeaponProfile profile, ReachProfile reach,
                                           int intervalTicks, boolean hasShotCone) {
        if (hasShotCone) {
            return RANGED;
        }
        if (profile == WeaponProfile.UNARMED) {
            return NATURAL;
        }
        // **Before reach and interval, because a tool is not classified by how it swings.** The
        // pala would read as medium or heavy by its numbers; "Herramienta" is the truthful answer
        // and it has to be asked first or the numbers win.
        if (isTool(profile)) {
            return TOOL;
        }
        if (reach != null && reach.distance() >= LONG_REACH) {
            return LONG;
        }
        // **Two questions, because one of them lies.** See HEAVY_REACH: the pala swings slower
        // than the warhammer, so the clock alone called a tool heavy and a weapon medium.
        if (intervalTicks >= HEAVY_TICKS && reach != null && reach.distance() >= HEAVY_REACH) {
            return HEAVY;
        }
        if (reach != null && reach.distance() < SHORT_REACH) {
            return SHORT;
        }
        return MEDIUM;
    }

    /**
     * Whether the profile is a tool rather than a weapon.
     *
     * <p>The pickaxe never fights and the pala does, and both are tools -- so the question is about
     * the item's role, not about whether it is allowed to swing. The hoe is a tool on the same
     * terms; the axe is not, and {@link #TOOL} says why.</p>
     */
    public static boolean isTool(WeaponProfile profile) {
        return profile == WeaponProfile.PICKAXE
                || profile == WeaponProfile.SHOVEL
                || profile == WeaponProfile.HOE;
    }
}
