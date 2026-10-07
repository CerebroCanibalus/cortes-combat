package com.cortes.combat.core.weapon;

import java.util.List;

/**
 * The five crafting grids, one per weapon, as data.
 *
 * <p>Every weapon in this mod has a shape you can recognise from its silhouette, which is a
 * design decision (DESIGN.md 5bis) and not a convenience: a player who has crafted one weapon
 * should be able to guess the next one. A vanilla sword grid for all five would have thrown that
 * away, and it is also the one thing that cannot be added later without invalidating every recipe
 * a player has already learned.</p>
 *
 * <p>Pure, so the arithmetic of a grid is testable: the count of each ingredient is derived here
 * rather than written a second time in the recipe provider. A grid that says four material pieces
 * and a recipe that lays out three is a recipe that cannot be crafted, and it would be a
 * datagen bug with no symptom until someone tried to use it.</p>
 *
 * <p>Notation, from DESIGN.md: {@code X} the tier's material, {@code /} a stick, {@code *} the
 * weapon's second ingredient, {@code #} empty.</p>
 *
 * <p><b>{@code #} is a shorthand for nothing.</b> Minecraft's shaped recipe format spells an empty
 * cell as a space, and it rejects a pattern that uses a symbol with no entry in the key -- so
 * {@code #} never reaches the JSON. It is written here because that is how DESIGN.md draws the
 * grids and because a grid full of spaces is unreadable in a source file.
 * {@link #toRecipeRows()} is the one place the translation happens and the recipe provider is its
 * only caller; the failure without it is
 * {@code IllegalStateException: Pattern references symbol '#' but it's not defined in the key},
 * which names the character and not the recipe.</p>
 */
public enum WeaponShape {

    /**
     * The ropera: a pure diagonal, tip up, one material and one extra above a stick.
     *
     * <pre>
     * *##
     * #X#
     * ##/
     * </pre>
     *
     * <p>No wooden version. A thin straight blade with no tier at the bottom is not something you
     * make out of sticks.</p>
     */
    ROPERA("ropera", List.of("*##", "#X#", "##/"), 1, 1, 1),

    /**
     * The halberd: three sticks running the other diagonal, four material pieces.
     *
     * <pre>
     * XX/
     * X/X
     * /##
     * </pre>
     *
     * <p>Four material pieces and three sticks: the most expensive grid in the mod, which is
     * right for the weapon with the longest reach and the only one that hits a crowd.</p>
     */
    ALABARDA("alabarda", List.of("XX/", "X/X", "/##"), 4, 3, 0),

    /**
     * The warhammer: a head across the top with a block at its centre, on a two-stick haft.
     *
     * <pre>
     * X*X
     * #/#
     * #/#
     * </pre>
     *
     * <p><b>The core is the interesting cell, and it is a BLOCK.</b> Not the tier's own material:
     * an iron block at every tier except netherite, which takes a gold block, because the head has
     * to be a single solid piece of something denser than the two ends, and a block says "denser"
     * with an item rather than with a number.</p>
     *
     * <p><b>And the block is what tells the head apart from a pickaxe's.</b> The three-wide head
     * over a vertical haft is the pickaxe's own silhouette, so the shape alone cannot be the
     * difference -- what makes it the warhammer's is the centre cell, and a block reads as a block
     * where an ingot read as one more of the same three. The playtest found it at the table: an
     * ingot in the middle is a pickaxe with a shorter haft.</p>
     */
    MARTILLO("martillo", List.of("X*X", "#/#", "#/#"), 2, 2, 1),

    /**
     * The dagger: the only wooden version in the mod, and the smallest grid.
     *
     * <pre>
     * X#
     * /#
     * </pre>
     *
     * <p>Two cells. It is the access weapon, the one a player can make on day one with nothing
     * but a tree, and a grid that takes six pieces would not be that.</p>
     */
    DAGA("daga", List.of("X#", "/#"), 1, 1, 0),

    /**
     * The shotel: three material pieces in the corner, the stick closing the outline.
     *
     * <pre>
     * ###
     * X##
     * XX/
     * </pre>
     *
     * <p>Three materials and one stick: the cheapest real weapon in the set, which pairs with its
     * role. It is fast, wide and short, and it is cheap to replace.</p>
     */
    SHOTEL("shotel", List.of("###", "X##", "XX/"), 3, 1, 0);

    private final String id;
    private final List<String> rows;
    private final int bodyPieces;
    private final int sticks;
    private final int extraPieces;

    WeaponShape(String id, List<String> rows, int bodyPieces, int sticks, int extraPieces) {
        this.id = id;
        this.rows = List.copyOf(rows);
        this.bodyPieces = bodyPieces;
        this.sticks = sticks;
        this.extraPieces = extraPieces;
    }

    public String id() {
        return id;
    }

    public List<String> rows() {
        return rows;
    }

    /**
     * The grid as the recipe format wants it, with every {@code #} turned into a space.
     *
     * <p>The only method here that exists for Minecraft rather than for the design, and the reason
     * it is a method and not a convention: {@code ShapedRecipePattern.of} validates the pattern
     * against the key and throws on a symbol with no entry, so a grid written with {@code #}
     * fails at datagen with {@code "Pattern references symbol '#' but it's not defined in the
     * key"} -- an error that names a character rather than the weapon whose grid was wrong.</p>
     */
    public List<String> toRecipeRows() {
        return rows.stream().map(row -> row.replace('#', ' ')).toList();
    }

    /** How many {@code X} the grid lays down. */
    public int bodyPieces() {
        return bodyPieces;
    }

    public int sticks() {
        return sticks;
    }

    /** How many {@code *} the grid lays down. Zero means the whole grid is body, stick and air. */
    public int extraPieces() {
        return extraPieces;
    }

    /** Whether this grid takes a second ingredient at all. */
    public boolean needsExtraIngredient() {
        return extraPieces > 0;
    }

    /**
     * Whether the grid lays down exactly the pieces it claims.
     *
     * <p>The counts above are declared, and this is where they are checked against the actual
     * characters. A grid edited without its counts is a recipe that silently needs the wrong
     * number of something, and the failure is "I have the items and it will not craft", which is
     * the least diagnosable report in the game.</p>
     */
    public boolean isSelfConsistent() {
        int body = 0;
        int stick = 0;
        int extra = 0;
        for (String row : rows) {
            for (int c = 0; c < row.length(); c++) {
                switch (row.charAt(c)) {
                    case 'X' -> body++;
                    case '/' -> stick++;
                    case '*' -> extra++;
                    case '#' -> { }
                    default -> {
                        return false;
                    }
                }
            }
        }
        return body == bodyPieces && stick == sticks && extra == extraPieces;
    }

    /** Every row the same width, which {@code ShapedRecipeBuilder} requires and does not say. */
    public boolean isRectangular() {
        int width = rows.get(0).length();
        for (String row : rows) {
            if (row.length() != width) {
                return false;
            }
        }
        return true;
    }
}
