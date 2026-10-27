package com.cortes.combat.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * El cache de colores del HUD: mismo texto, mismo slot; texto nuevo, parseo nuevo.
 *
 * <p><b>El riesgo de un cache es la invalidacion</b>, no el ahorro: un color que deja de
 * actualizarse se reporta como "la config no hace nada". La regla es la comparacion del texto
 * crudo (que cambia cuando el config recarga), y estos tres tests son lo que la mantiene
 * honesta. (μ_μ)</p>
 */
class HudPaletteTest {

    @Test
    @DisplayName("the same text is not parsed twice")
    void theSameTextIsNotParsedTwice() {
        HudPalette.Slot first = HudPalette.refresh(null, "#C06BFF", 0xFFFFFF);
        HudPalette.Slot second = HudPalette.refresh(first, "#C06BFF", 0xFFFFFF);
        assertSame(first, second, "mismo texto -> mismo slot, sin volver a parsear");
        assertEquals(0xC06BFF, second.rgb());
    }

    @Test
    @DisplayName("a new text is a new colour, which is what /reload produces")
    void aNewTextReparses() {
        HudPalette.Slot first = HudPalette.refresh(null, "#C06BFF", 0xFFFFFF);
        HudPalette.Slot second = HudPalette.refresh(first, "#FF0000", 0xFFFFFF);
        assertNotSame(first, second, "un texto distinto tiene que re-parsearse");
        assertEquals(0xFF0000, second.rgb(), "y el color nuevo es el nuevo, no el viejo");
        assertEquals("#FF0000", second.raw(), "el slot recuerda de que texto salio");
    }

    @Test
    @DisplayName("a broken text falls back, and the fallback is not cached as the raw")
    void brokenTextFallsBack() {
        HudPalette.Slot slot = HudPalette.refresh(null, "no-es-un-color", 0x123456);
        assertEquals(0x123456, slot.rgb(), "el fallback es el que pidio el llamador");
        assertEquals("no-es-un-color", slot.raw(), "y el texto crudo queda para comparar");
        HudPalette.Slot again = HudPalette.refresh(slot, "no-es-un-color", 0x999999);
        assertSame(slot, again,
                "un texto roto tampoco se re-parsea: el resultado no cambia hasta que cambie el texto");
    }
}
