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
 * <h2>The second report that rewired it (2026-10-27)</h2>
 *
 * <p>"S&iacute; cubre, pero da el efecto de golpe y adem&aacute;s no se consume." En la pata
 * 1.20.1 el evento corre <em>dentro</em> de {@code actuallyHurt}, y el bytecode (javap, offsets
 * 303-317) muestra que vanilla ya subi&oacute; {@code invulnerableTime} a 20 en la rama else
 * <b>antes</b> de llegar ah&iacute;: cada golpe nuevo llegaba con 20 puesto y el gate lo leia
 * como ventana abierta -- el primer bloqueo cobr&oacute; cero y nunca volvi&oacute; a cobrar.
 * El valor que decide es el <b>pre-gate</b>, y en esta pata se captura en
 * {@code LivingAttackEvent}, que corre al top de {@code hurt()} -- el mismo valor que la pata
 * 1.21.1 recibe por el orden de su propio evento. ∴ la pata 1.21.1 todav&iacute;a lee el campo
 * vivo (correcto all&aacute;), la 1.20.1 lee la captura, y ambos nombran el mismo n&uacute;mero
 * con umbral 10.</p>
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
     * <p>All three facts are one sentence of code: {@code if (inIFrames || !last.shouldCharge(...))}.
     * The window predicate itself has to be the vanilla one -- {@code invulnerableTime > 10} behind
     * the bypass tag -- because "the i-frames the player knows" is that exact threshold, not a
     * number we choose again.</p>
     *
     * <p>The two legs ask for it at different doors, and each door is asserted separately. In the
     * 1.21.1 leg the damage event runs before vanilla writes, so the live field IS the answer. In
     * the 1.20.1 leg the answer travels through the capture ({@code onAttackStart} &rarr;
     * {@code CombatState.guardPreInvuln}), and {@code onShieldBlock} must NOT name the live field:
     * there, a live-field read is not a style choice, it is the bug &mdash; by the time the event
     * runs, vanilla has already written 20 into it.</p>
     */
    @Test
    @DisplayName("the charge decision asks the invulnerability window, in both legs")
    void theChargeDecisionAsksTheWindow() {
        String main = methodBody(MAIN, "onShieldBlock");
        assertTrue(main.contains("invulnerableTime > 10"),
                MAIN + " never asks the window: the second mob's hit would charge again, "
                        + "and the report stays true");
        sharedGateFacts(main, MAIN);

        String v120 = methodBody(V120, "onShieldBlock");
        assertTrue(v120.contains("guardPreInvuln("),
                V120 + " does not read the captured pre-gate value: it is back on the live "
                        + "field, and every fresh hit arrives with the 20 vanilla just wrote");
        assertFalse(v120.contains("player.invulnerableTime"),
                V120 + " reads the live invulnerableTime inside onShieldBlock: in this leg that "
                        + "field is already 20 by event time -- the exact bug of the round, "
                        + "'si cubre pero nunca se consume'");
        sharedGateFacts(v120, V120);

        // Y la captura existe, y captura el campo vivo en el unico lugar donde el campo ancora
        // vale: el top de hurt(), antes de la escritura de la rama else.
        String capture = methodBody(V120, "onAttackStart");
        assertTrue(capture.contains("LivingAttackEvent"),
                V120 + " lost the capture handler: with nothing recording the top-of-hurt value, "
                        + "guardPreInvuln is stale and the gate answers from the last hit");
        assertTrue(capture.contains("player.invulnerableTime"),
                V120 + "'s capture stopped reading the field: the value it stores would be a "
                        + "guess, not the pre-gate answer");
    }

    /** What both doors share: the bypass tag, the free branch, and the stamp ordered after it. */
    private static void sharedGateFacts(String body, Path file) {
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

    /**
     * The handler's source, from its signature on, with the {@code //} comments stripped.
     *
     * <p>Sin la tira de comentarios, un guard de fuente lee prosa y muerde su propia
     * explicacion -- ya pago tres rondas el mismo mordisco ({@code ModifyExpressionValue}
     * nombrado en un javadoc, {@code SoundPoolTest} tirando la carpeta, el guard del reactor
     * que encontro su comentario). El texto que aserciona es codigo; el texto que explica
     * vive en los comentarios y no debe contar. (⌒‿⌒)</p>
     */
    private static String methodBody(Path file, String method) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            int start = source.indexOf(method + "(");
            assertFalse(start < 0, file + " lost the " + method + " handler");
            return stripLineComments(source.substring(start));
        } catch (IOException e) {
            throw new AssertionError("the handler source is unreadable: " + file, e);
        }
    }

    private static String stripLineComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        for (String line : code.split("\n", -1)) {
            int cut = line.indexOf("//");
            out.append(cut < 0 ? line : line.substring(0, cut)).append('\n');
        }
        return out.toString();
    }
}
