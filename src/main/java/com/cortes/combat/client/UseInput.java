package com.cortes.combat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * <b>La tecla de USAR de vanilla, preguntada a la configuracion y nunca al raton.</b>
 *
 * <h2>El reporte que creo esta clase</h2>
 *
 * <p>Un jugador que no usa el boton derecho como "usar" reporto dos estados imposibles: podia
 * "usar" un escudo y ver la animacion sin bloquear nada, y el arcabuz solo cargaba mirando un
 * bloque, con el sonido de recarga repitiendose sin cargar jamas. La causa era una sola: la
 * guardia, la especial y el flag de "boton sostenido" escuchaban {@code MOUSE_BUTTON_RIGHT} a
 * mano, mientras vanilla mueve el item con {@code options.keyUse}. <b>El boton fisico y el boton
 * logico eran dos preguntas distintas y el mod contestaba la primera.</b></p>
 *
 * <h2>Por que se pregunta la tecla y no el evento</h2>
 *
 * <p>Vanilla ya tiene la respuesta y la mantiene: {@code KeyMapping.matchesMouse} para un evento
 * de raton, {@code matches} para uno de teclado (que tambien resuelve bindings por scancode), y
 * {@code isDown()} para el estado sostenido -- la misma pregunta que {@code handleKeybinds} hace
 * para arrancar el uso del item. <b>Una segunda copia de "cual es el boton de usar" es
 * exactamente como el cliente y vanilla terminan discrepando.</b></p>
 *
 * <p>El estado sostenido se confirma ademas contra GLFW, y no por desconfianza del binding: GLFW
 * no entrega el release cuando la ventana pierde el foco con una tecla apretada, y un flag que
 * solo se mueve por eventos deja al servidor creyendo que el jugador sigue apretando. Es la
 * misma regla que {@link InputLiveness} ya aplica al swing y a la guardia. (⌒‿⌒)</p>
 */
public final class UseInput {

    private UseInput() {
    }

    /** Si un evento de raton es la tecla de usar configurada. */
    public static boolean matchesMouse(int button) {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.keyUse.matchesMouse(button);
    }

    /** Si un evento de teclado es la tecla de usar configurada, por keysym o por scancode. */
    public static boolean matchesKey(int keySym, int scanCode) {
        Minecraft mc = Minecraft.getInstance();
        return mc.options != null && mc.options.keyUse.matches(keySym, scanCode);
    }

    /**
     * Si la tecla de usar esta fisicamente apretada ahora mismo.
     *
     * <p>Pide las dos cosas: que el binding diga "abajo" (lo que respeta modificadores y
     * contexto de conflicto de NeoForge/Forge) y que el dispositivo lo confirme. La segunda
     * mitad es la que cierra el agujero de foco. (￣ε￣＠)</p>
     */
    public static boolean isDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !mc.options.keyUse.isDown()) {
            return false;
        }
        return physicallyDown(mc.options.keyUse);
    }

    private static boolean physicallyDown(KeyMapping use) {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        if (window == null) {
            return false;
        }
        InputConstants.Key key = use.getKey();
        long handle = window.getWindow();
        return switch (key.getType()) {
            case MOUSE -> GLFW.glfwGetMouseButton(handle, key.getValue())
                    != GLFW.GLFW_RELEASE;
            case KEYSYM -> GLFW.glfwGetKey(handle, key.getValue()) == GLFW.GLFW_PRESS;
            // Un binding por scancode no tiene vuelta desde GLFW; se confia en el binding. Los
            // bindings de vanilla por defecto son de los otros dos tipos. (μ_μ)
            default -> true;
        };
    }
}
