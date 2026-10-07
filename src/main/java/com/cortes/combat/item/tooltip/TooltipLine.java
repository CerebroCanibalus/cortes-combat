package com.cortes.combat.item.tooltip;

import java.util.List;

/**
 * One line of a weapon tooltip: a translation key, the arguments to put in it, and its colour.
 *
 * <p><b>A record instead of a {@code Component} so the whole decision layer stays testable.</b>
 * {@code Component} is a Minecraft type and nothing in {@code src/test} can name one, so a tooltip
 * built out of them could only be checked by launching the game. Built this way, the question
 * "which lines, in what order, with which numbers, in which colour, and which of them are left out"
 * is answered by a bare JVM, and the only line that touches the game is the one that turns a key,
 * its arguments and a {@link TooltipColor} into a component.</p>
 *
 * @param key   the translation key, without a namespace
 * @param args  already-formatted strings, because the tooltip language supports {@code %s} and
 *              nothing else -- {@code %.1f} is rejected at render time
 * @param color which of the tooltip's colours this line is drawn in
 */
public record TooltipLine(String key, List<String> args, TooltipColor color) {

    public TooltipLine {
        args = List.copyOf(args);
    }

    /** A line with nothing to interpolate. */
    public static TooltipLine plain(String key, TooltipColor color) {
        return new TooltipLine(key, List.of(), color);
    }

    /** A line with exactly one value in it, which is every number in this tooltip. */
    public static TooltipLine of(String key, String value, TooltipColor color) {
        return new TooltipLine(key, List.of(value), color);
    }

    /**
     * A line with two values, for the two places a weapon explains a pair rather than a number.
     *
     * <p><b>Two arguments and not one joined string, and a test is what said so.</b> The maza's
     * mechanic line first carried {@code "50.0/31.0"} in a single argument, and the tooltip's own
     * "every argument is one number" guard caught it: two decimal points in one argument is a
     * compound value, and a compound value is exactly what makes a translation impossible -- the
     * order of the two halves is grammar, and a joined string freezes it to Spanish.</p>
     */
    public static TooltipLine of(String key, String first, String second, TooltipColor color) {
        return new TooltipLine(key, List.of(first, second), color);
    }
}
