package com.cortes.combat.guard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * That a hit inside the defender's invulnerability window does not cost the guard a point.
 *
 * <h2>The report that wrote this</h2>
 *
 * <p>"Varios mobs pegan al mismo tiempo y se agotan toda la aguante como si no tuvieran
 * i-frames -- y lo mismo entre jugadores." Vanilla resolves the shield <b>before</b> it looks at
 * {@code invulnerableTime} (1.21.1: lines 1164 against 1190), so the second mob's hit paid a
 * full point even when the {@code hurt()} gate would have swallowed the hit entirely. The fix
 * puts the same window the player expects in front of the charge: while it is open, the block
 * still stands but the point is not spent.</p>
 *
 * <p>The handlers need a live {@code Player}, so this reads the source of <b>both legs</b> --
 * brittle by design, like every wiring test here. Sabotaging either half must fail its own
 * assertion, not a stranger's.</p>
 */
class GuardIFramesWiringTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "cortes", "combat",
            "guard", "GuardHandler.java");

    private static final Path V120 = Path.of("src", "v120", "java", "com", "cortes", "combat",
            "guard", "GuardHandler.java");

    /**
     * The charge decision, in both handlers, names the window and shares the free branch.
     *
     * <p>All three facts are one sentence of code: {@code if (inIFrames ||
     * !last.shouldCharge(...))}. The window predicate itself has to be the vanilla one --
     * {@code invulnerableTime > 10} behind the bypass tag -- because "the i-frames the player
     * knows" is that exact threshold, not a number we choose again.</p>
     */
    @Test
    @DisplayName("the charge decision asks the invulnerability window, in both legs")
    void theChargeDecisionAsksTheWindow() {
        for (Path file : List.of(MAIN, V120)) {
            String body = methodBody(file, "onShieldBlock");
            assertTrue(body.contains("invulnerableTime > 10"),
                    file + " never asks the window: the second mob's hit would charge again, "
                            + "and the report stays true");
            assertTrue(body.contains("BYPASSES_COOLDOWN"),
                    file + " does not exempt the bypassing sources: fire and void hits inside the "
                            + "window would go free, and those are exactly the hits vanilla lets "
                            + "through");
            int gate = body.indexOf("if (inIFrames ||");
            assertTrue(gate >= 0,
                    file + " does not give the window the free branch: either the gate is written "
                            + "somewhere it cannot share the already-paid path, or the free branch "
                            + "lost the new condition");
            assertTrue(body.indexOf("shouldCharge(now, attackerId, targetId)") > gate,
                    file + " reversed the order: the stamp answering alone would keep charging the "
                            + "window's hits");
        }
    }

    /**
     * The free branch of the window does not spend anything before it returns.
     *
     * <p>The shared branch already returns without touching {@code setGuard} / {@code setLastBlock}
     * / {@code setContragolpe}; this pins it the cheap way -- the spending calls live <b>after</b>
     * the free branch's return, so a refactor that "simplifies" the branch into the charged path
     * still reads wrong here.</p>
     */
    @Test
    @DisplayName("the free branch returns before anything is spent")
    void theFreeBranchReturnsBeforeAnythingSpends() {
        String charged = methodBody(MAIN, "onShieldBlock");
        int freeReturn = charged.indexOf("if (inIFrames ||");
        int spend = charged.indexOf("CombatState.setGuard(player");
        assertTrue(freeReturn >= 0 && spend > freeReturn,
                "**the charged spend moved above the free branch.** The free path returns early; "
                        + "anything spent before it is spent on hits that should not pay");
    }

    private static String methodBody(Path file, String method) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            int start = source.indexOf(method + "(");
            assertFalse(start < 0, file + " lost the " + method + " handler");
            return source.substring(start);
        } catch (IOException e) {
            throw new AssertionError("the handler source is unreadable: " + file, e);
        }
    }
}
