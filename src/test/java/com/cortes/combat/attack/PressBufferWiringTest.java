package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That the press buffer is wired on both ends and in the order that makes it work.
 *
 * <h2>Why this reads the source</h2>
 *
 * <p>All four files here need a live client or server, so this is the same brittle-by-design
 * wiring read every handler gets. The failure it exists for is the one this project keeps
 * paying: <b>a line that compiles, keeps the suite green and leaves a feature switched
 * off</b> -- a buffer that is written but never fired, or fired through a gate that never
 * passes because the cooldown tick runs after it.</p>
 */
class PressBufferWiringTest {

    private static final Path SWING = Path.of("src", "main", "java", "com", "cortes", "combat",
            "client", "SwingInputHandler.java");

    private static final Path TICKER = Path.of("src", "main", "java", "com", "cortes", "combat",
            "attack", "CombatTicker.java");

    private static final Path V120_TICKER = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "attack", "CombatTicker.java");

    private static final Path TICKER_1 = Path.of("src", "main", "java", "com", "cortes", "combat",
            "net", "ModNetworking.java");

    private static final Path TICKER_120 = Path.of("src", "v120", "java", "com", "cortes",
            "combat", "net", "ModNetworking.java");

    /**
     * Both refusal branches a parked press can survive -- cooldown and guard -- park the press.
     *
     * <p><b>And only those two.</b> The other refusals ("not a weapon", "ranged", "screen",
     * "entity without life") are answers about what the player is pointing at, and parking one
     * would fire a swing at a thing that never was a swinging target. Counting the parks is how
     * this stays two.</p>
     */
    @Test
    @DisplayName("exactly the two refusals park the press, client side")
    void exactlyTheTwoRefusalsParkIt() {
        String source = read(SWING);
        int parks = source.split("parkedPress = tick;", -1).length - 1;
        assertTrue(parks == 2,
                "**there are " + parks + " parking sites, and the honest count is two.** Each "
                        + "extra one is a refusal about direction being treated as a refusal "
                        + "about timing, which is a swing fired at a thing that refused a swing "
                        + "for being the wrong kind of thing");
        int fired = source.indexOf("PressBuffer.shouldFire(parkedPress");
        int lastPark = source.lastIndexOf("parkedPress = tick;");
        assertTrue(fired > lastPark,
                "the park has to come before the fire in the file, or the fired press reads "
                        + "before it was ever parked");
    }

    /**
     * The fire runs the press, clears the park first, and guards against a button already up.
     */
    @Test
    @DisplayName("the fired press goes through press() with the park spent on success only")
    void theFireRunsTheRealPress() {
        String source = read(SWING);
        String body = source.substring(source.indexOf("parkedPress != PressBuffer.EMPTY"));
        assertTrue(body.indexOf("parkedPress = PressBuffer.EMPTY;") > body.indexOf("PressBuffer.shouldFire(parkedPress"),
                "the park must clear before press() runs, or the press's own top-of-method clear "
                        + "and this one fight over a value the press then re-parks");
        assertTrue(source.contains("canFireTheParkedPress(mc, player)"),
                "without the gate helper the fire would run with no question at all");
        String gate = source.substring(source.indexOf("canFireTheParkedPress(Minecraft"));
        assertTrue(gate.contains("buttonIsPhysicallyDown()"),
                "**the gate does not ask whether the button is still down.** A tap parked on a "
                        + "refusal has to stay dead once the finger is up, or the buffer is "
                        + "longer than the player's own click -- the 'se arrepiente' rule");
        assertTrue(gate.contains("ready()"),
                "and the cooldown gate, which is what the window was waiting for");
        assertTrue(gate.contains("predictedHolding()"),
                "and the guard gate, which is the other refusal the window may wait behind");
    }

    /** The cooldown tick runs first, and the buffer can fire in the very tick it becomes ready. */
    @Test
    @DisplayName("the buffer tick runs right after the cooldown tick, in both legs")
    void theBufferTickAfterTheCooldownTickBothLegs() {
        for (Path ticker : List.of(TICKER, V120_TICKER)) {
            String source = read(ticker);
            int cooldown = source.indexOf("tickCooldown(player);");
            int buffer = source.indexOf("tickSwingBuffer(player);");
            assertTrue(cooldown >= 0, ticker + " lost its cooldown tick");
            assertTrue(buffer > cooldown,
                    "**the buffer tick moved before the cooldown tick.** A cooldown that dies "
                            + "this tick must free the buffer in the same tick; run first, the "
                            + "buffer would wait a whole extra tick and the fire would not be "
                            + "'as soon as it can' any more");
            String onLog = source.substring(source.indexOf("AttackCooldown.endCharge(player);"));
            assertTrue(onLog.contains("setSwingBuffer(player, PressBuffer.EMPTY)"),
                    ticker + " does not clear the park on login: a session that opens with a "
                            + "buffer is a press from a connection that no longer exists");
        }
    }

    /** The server parks a press it refused, and only on refuse. */
    @Test
    @DisplayName("the server parks exactly the refused press, in both legs")
    void theServerParksTheRefusedPress() {
        for (Path hand : List.of(TICKER_1, TICKER_120)) {
            String source = read(hand);
            assertTrue(source.contains("boolean opened = AttackCooldown.beginCharge(player);"),
                    hand + " ignored the answer: without it there is no way to park a refused press");
            int park = source.indexOf("if (!opened) {");
            assertTrue(park >= 0, hand + " parks nothing");
            assertTrue(source.contains("setSwingBuffer(player, player.level().getGameTime())"),
                    hand + " parks without the tick, which is what the window is measured from");
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("unreadable: " + file, e);
        }
    }
}
