package com.cortes.combat.client;

/**
 * El color del HUD, como dos preguntas puras: de texto a {@code 0xRRGGBB}, y cual de los dos
 * colores corresponde ahora.
 *
 * <p><b>Puro a proposito.</b> Un {@code #RRGGBB} escrito en la config es la clase de campo que
 * se rompe en silencio: un valor con una letra de mas no tira un error, devuelve un numero
 * raro, y el HUD se pinta de un color que nadie eligio. Aca el parseo tiene reglas escritas y un
 * fallback explicito, y se testea en JVM pelada. (⌒‿⌒)</p>
 */
public final class HudColors {

    /** El blanco: multiplicar el arte por el lo deja tal cual. */
    public static final int WHITE = 0xFFFFFF;

    private HudColors() {
    }

    /**
     * De {@code "#RRGGBB"}, {@code "RRGGBB"}, {@code "0xRRGGBB"} o un nombre de seis digitos a un
     * entero RGB. Cualquier otra cosa vuelve el fallback -- nunca lanza y nunca devuelve negro
     * por accidente, porque "un color invalido" y "el color negro elegido" no pueden parecerse.
     */
    public static int parse(String raw, int fallback) {
        if (raw == null) {
            return fallback;
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x") || text.startsWith("0X")) {
            text = text.substring(2);
        }
        if (text.length() != 6) {
            return fallback;
        }
        int rgb = 0;
        for (int i = 0; i < 6; i++) {
            int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) {
                return fallback;
            }
            rgb = (rgb << 4) | digit;
        }
        return rgb;
    }

    /**
     * El color que toca: el del flash si hay un flash corriendo, el de reposo si no.
     *
     * <p><b>El flash no se tinta.</b> El color de un golpe, una muerte o un bloqueo lleva
     * informacion -- el jugador lo aprendio mirando -- asi que un color de reposo configurable
     * no puede pisarlo. Lo que se configura es como se ve el HUD cuando no esta contando nada.</p>
     */
    public static int flashOr(int flashRgb, boolean flashing, int restingRgb) {
        return flashing ? flashRgb : restingRgb;
    }

    /**
     * Lo mismo, con el estado de apuntado en el medio: reposo, apuntando, y por encima de los dos
     * el flash.
     */
    public static int stateOr(int flashRgb, boolean flashing, boolean aiming, int idleRgb,
                              int aimRgb) {
        if (flashing) {
            return flashRgb;
        }
        return aiming ? aimRgb : idleRgb;
    }
}
