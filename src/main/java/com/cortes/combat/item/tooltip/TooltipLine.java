package com.cortes.combat.item.tooltip;

import java.util.List;

/**
 * One line of a weapon tooltip: a translation key and the arguments to put in it.
 *
 * <p><b>A record instead of a {@code Component} so the whole decision layer stays testable.</b>
 * {@code Component} is a Minecraft type and nothing in {@code src/test} can name one, so a tooltip
 * built out of them could only be checked by launching the game. Built this way, the question
 * "which lines, in what order, with which numbers, and which of them are left out" is answered by
 * a bare JVM, and the only line that touches the game is the one that turns a key and its
 * arguments into a component.</p>
 *
 * @param key  the translation key, without a namespace
 * @param args already-formatted strings, because the tooltip language supports {@code %s} and
 *             nothing else -- {@code %.1f} is rejected at render time
 */
public record TooltipLine(String key, List<String> args) {

    public TooltipLine {
        args = List.copyOf(args);
    }

    /** A line with nothing to interpolate. */
    public static TooltipLine plain(String key) {
        return new TooltipLine(key, List.of());
    }

    /** A line with exactly one value in it, which is every number in this tooltip. */
    public static TooltipLine of(String key, String value) {
        return new TooltipLine(key, List.of(value));
    }
}