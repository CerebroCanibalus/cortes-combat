package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El color del HUD como texto: lo que se acepta, lo que cae al fallback y cual gana cuando hay
 * un flash corriendo.
 *
 * <p><b>El caso que importa es el invalido.</b> Un {@code #RRGGBB} mal escrito en la config no
 * tira un error: devuelve un numero raro y el HUD se pinta de un color que nadie eligio. Aca el
 * fallback es explicito y el negro elegido no se puede confundir con un parseo roto.
 * (⌒‿⌒)</p>
 */
class HudColorsTest {

    @Test
    @DisplayName("the three ways a player writes a hex colour")
    void theThreeWaysToWriteHex() {
        assertEquals(0xC06BFF, HudColors.parse("#C06BFF", 0x000000), "con numeral");
        assertEquals(0xC06BFF, HudColors.parse("C06BFF", 0x000000), "sin numeral");
        assertEquals(0xC06BFF, HudColors.parse("0xC06BFF", 0x000000), "con prefijo 0x");
        assertEquals(0xC06BFF, HudColors.parse("  #c06bff  ", 0x000000),
                "minusculas y espacios: la config no es un compilador");
    }

    @Test
    @DisplayName("anything malformed falls back, and never to black by accident")
    void malformedFallsBack() {
        assertEquals(0x123456, HudColors.parse("red", 0x123456), "un nombre no es un color");
        assertEquals(0x123456, HudColors.parse("#FFF", 0x123456), "tres digitos no alcanzan");
        assertEquals(0x123456, HudColors.parse("#GGGGGG", 0x123456), "G no es un digito hex");
        assertEquals(0x123456, HudColors.parse("", 0x123456), "vacio");
        assertEquals(0x123456, HudColors.parse(null, 0x123456), "ausente");
        assertEquals(0x000000, HudColors.parse("#000000", 0x123456),
                "el negro ELEGIDO si es negro: el fallback no lo pisa");
    }

    @Test
    @DisplayName("the flash keeps its colour and the resting state takes the config's")
    void theFlashIsNotTinted() {
        int red = 0xFF2020;
        assertEquals(red, HudColors.flashOr(red, true, 0x00FF00),
                "un golpe pinta su color: eso es informacion, no decoracion");
        assertEquals(0x00FF00, HudColors.flashOr(red, false, 0x00FF00),
                "sin flash manda el color de reposo de la config");
    }

    @Test
    @DisplayName("idle, aiming, and the flash above both")
    void idleAimingAndFlash() {
        int flash = 0xFF0000;
        int idle = 0xFFFFFF;
        int aim = 0xFFAA00;
        assertEquals(idle, HudColors.stateOr(flash, false, false, idle, aim), "reposo");
        assertEquals(aim, HudColors.stateOr(flash, false, true, idle, aim), "apuntando");
        assertEquals(flash, HudColors.stateOr(flash, true, true, idle, aim),
                "el flash gana sobre el apuntado: un golpe es mas noticia que una mira");
    }
}
