package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>El boton de usar se pregunta al binding, y no al raton.</b>
 *
 * <h2>Por que esto es un guard de fuente</h2>
 *
 * <p>Las cuatro clases que escuchan entrada necesitan un cliente con ventana, y la JVM de tests
 * no puede cargarlas (implementan eventos de Minecraft). Lo unico comprobable sin abrir el juego
 * es <b>de donde leen la tecla</b>, y eso es exactamente el bug que se pago: la guardia y la
 * especial escuchaban {@code MOUSE_BUTTON_RIGHT} a mano, vanilla mueve el item con
 * {@code options.keyUse}, y un jugador que rebindea "usar" quedaba con la animacion de escudo sin
 * bloqueo y un arcabuz que solo cargaba mirando un bloque.</p>
 *
 * <p>El guard lee los archivos <b>sin comentarios</b>: el javadoc de estas clases nombra el boton
 * derecho para explicar el bug, y un guard textual sobre el texto crudo encontraria su propia
 * explicacion. Es la leccion que este proyecto pago tres veces. (⌒‿⌒)</p>
 */
class UseBindingWiringTest {

    private static final List<Path> HANDLERS = List.of(
            Path.of("src", "main", "java", "com", "cortes", "combat", "client",
                    "GuardInputHandler.java"),
            Path.of("src", "main", "java", "com", "cortes", "combat", "client",
                    "SpecialInputHandler.java"),
            Path.of("src", "v120", "java", "com", "cortes", "combat", "client",
                    "GuardInputHandler.java"),
            Path.of("src", "v120", "java", "com", "cortes", "combat", "client",
                    "SpecialInputHandler.java"));

    private static final Path USE_INPUT = Path.of("src", "main", "java", "com", "cortes",
            "combat", "client", "UseInput.java");

    private static final Path SWING = Path.of("src", "main", "java", "com", "cortes", "combat",
            "client", "SwingInputHandler.java");

    @Test
    @DisplayName("guardia y especial escuchan la tecla de usar, en las dos patas")
    void theUseHandlersAskTheBinding() {
        for (Path handler : HANDLERS) {
            String code = code(handler);
            assertTrue(code.contains("UseInput.matchesMouse("),
                    handler + " no pregunta el binding en el evento de raton: volvio a comparar "
                            + "el boton derecho a mano");
            assertTrue(code.contains("UseInput.matchesKey("),
                    handler + " no escucha el evento de teclado: un 'usar' bindeado a una letra "
                            + "no llega");
            assertFalse(code.contains("MOUSE_BUTTON_RIGHT"),
                    handler + " volvio a clavar el boton derecho: ese es el bug entero");
        }
    }

    @Test
    @DisplayName("el estado sostenido tambien sale del binding")
    void theHeldFlagAsksTheSameQuestion() {
        for (Path handler : HANDLERS) {
            if (!handler.toString().contains("GuardInputHandler")) {
                continue;
            }
            String code = code(handler);
            assertTrue(code.contains("return UseInput.isDown();"),
                    handler + " under the physical-down question: a rebinded use key would leave "
                            + "the guard latched to a mouse nobody is pressing");
        }
        for (Path handler : HANDLERS) {
            if (!handler.toString().contains("SpecialInputHandler")) {
                continue;
            }
            String code = code(handler);
            assertTrue(code.contains("boolean down = UseInput.isDown();"),
                    handler + " no deriva el flag de recarga de la tecla de usar: la recarga se "
                            + "cancelaria sola y el sonido se repetiria sin cargar");
        }
    }

    @Test
    @DisplayName("UseInput pregunta los dos dispositivos y el binding, no el raton")
    void useInputIsTheSingleDoor() {
        String code = code(USE_INPUT);
        assertTrue(code.contains("options.keyUse.matchesMouse("),
                "el evento de raton no se compara contra keyUse");
        assertTrue(code.contains("options.keyUse.matches("),
                "el evento de teclado no se compara contra keyUse");
        assertTrue(code.contains("options.keyUse.isDown()"),
                "el estado sostenido no pregunta el binding");
        assertTrue(code.contains("glfwGetMouseButton") && code.contains("glfwGetKey"),
                "falta la confirmacion fisica: un release perdido al perder foco dejaria el uso "
                        + "pegado");
    }

    @Test
    @DisplayName("una tecla sostenida dispara UNA especial, no una cada cuatro ticks")
    void theSpecialIsEdgeTriggered() {
        // Vanilla repite startUseItem cada 4 ticks mientras keyUse esta abajo, y en un arma que
        // no arranca un uso de item eso es un useItemOn nuevo cada 4 ticks. Sin el latch, el
        // arcabuz arranca y corta la recarga en bucle: el "spam de sonidos sin cargar".
        for (Path handler : HANDLERS) {
            if (!handler.toString().contains("SpecialInputHandler")) {
                continue;
            }
            String code = code(handler);
            assertTrue(code.contains("if (firedThisHold)"),
                    handler + " no tiene latch: la repeticion de vanilla vuelve a disparar");
            assertTrue(code.contains("firedThisHold = true;"),
                    handler + " nunca gasta el press");
            assertTrue(code.contains("firedThisHold = false;"),
                    handler + " nunca lo rearma al soltar la tecla");
        }
    }

    @Test
    @DisplayName("el ataque sigue siendo el boton izquierdo: este cambio no lo toco")
    void theAttackButtonIsUntouched() {
        String code = code(SWING);
        assertTrue(code.contains("MOUSE_BUTTON_LEFT"),
                "el swing perdio su boton izquierdo: el reporte era de 'usar', no del ataque");
    }

    /** El archivo sin comentarios de linea ni de bloque: lo que se afirma es codigo. */
    private static String code(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            StringBuilder out = new StringBuilder(source.length());
            boolean block = false;
            for (String line : source.split("\n", -1)) {
                StringBuilder kept = new StringBuilder();
                int i = 0;
                while (i < line.length()) {
                    if (block) {
                        int end = line.indexOf("*/", i);
                        if (end < 0) {
                            i = line.length();
                        } else {
                            block = false;
                            i = end + 2;
                        }
                    } else if (line.startsWith("/*", i)) {
                        block = true;
                        i += 2;
                    } else if (line.startsWith("//", i)) {
                        i = line.length();
                    } else {
                        kept.append(line.charAt(i));
                        i++;
                    }
                }
                out.append(kept).append('\n');
            }
            return out.toString();
        } catch (Exception e) {
            throw new AssertionError("unreadable: " + file, e);
        }
    }
}
