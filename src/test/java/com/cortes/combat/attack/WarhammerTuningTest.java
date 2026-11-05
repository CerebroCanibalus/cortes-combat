package com.cortes.combat.attack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los dos numeros del martillo, y las tres preguntas que hacen.
 *
 * <p><b>Puro a proposito.</b> Ningun tipo de Minecraft entra en
 * {@link WarhammerTuning}, asi que estas tres preguntas -- "el tope recorta", "la penetracion
 * deja la fraction que queda", "el default es el comportamiento de siempre" -- se contestan en
 * una JVM pelada. Un test que necesita el juego para confirmar que un numero se recorta es un
 * test que no se va a correr nunca.</p>
 */
class WarhammerTuningTest {

    @Test
    @DisplayName("el tope recorta el golpe que lo pasa, y no toca el que queda debajo")
    void theCapTruncates() {
        WarhammerTuning capped = new WarhammerTuning(20.0F, 1.0F);

        assertEquals(20.0F, capped.capDamage(45.0F), 1.0E-6,
                "un golpe de 45 con tope de 20 vale 20");

        assertEquals(12.0F, capped.capDamage(12.0F), 1.0E-6,
                "un golpe de 12 con tope de 20 vale 12: un tope no empuja el dano hacia arriba");

        assertEquals(20.0F, capped.capDamage(20.0F), 1.0E-6,
                "y justo en el tope vale lo mismo");
    }

    @Test
    @DisplayName("cero es sin tope, y no un golpe que no hace dano")
    void zeroIsNoCap() {
        WarhammerTuning uncapped = new WarhammerTuning(0.0F, 1.0F);

        assertEquals(45.0F, uncapped.capDamage(45.0F), 1.0E-6,
                "cero es 'sin tope'. Un tope de cero que dejara el dano en cero seria un martillo "
                        + "que no hace dano, y el default del archivo tiene que ser el "
                        + "comportamiento de siempre");

        assertEquals(0.0F, uncapped.capDamage(0.0F), 1.0E-6,
                "y cero de dano sigue siendo cero de dano");
    }

    @Test
    @DisplayName("la penetracion decide si la armadura participa, y el default la ignora entera")
    void thePenetrationDecidesTheArmour() {
        assertTrue(WarhammerTuning.DEFAULT.bypassesArmour(),
                "**el default es el comportamiento de antes**: el golpe cargado del martillo "
                        + "atravesaba la armadura entera");

        assertFalse(new WarhammerTuning(0.0F, 0.5F).bypassesArmour(),
                "con la mitad de penetracion la armadura participa, y por eso el golpe necesita "
                        + "otra fuente de dano");

        assertTrue(new WarhammerTuning(0.0F, 1.0F).bypassesArmour(),
                "con cien sigue sin participar");

        assertEquals(0.5F, new WarhammerTuning(0.0F, 0.5F).armourScale(), 1.0E-6,
                "la fraccion que queda de la armadura es la que no se ignora");

        assertEquals(1.0F, new WarhammerTuning(0.0F, 0.0F).armourScale(), 1.0E-6,
                "con cero de penetracion queda toda la armadura");
    }

    @Test
    @DisplayName("un numero fuera de rango se deja en rango, no se propaga")
    void outOfRangeIsClamped() {
        WarhammerTuning crazy = new WarhammerTuning(-5.0F, 4.0F);

        assertEquals(0.0F, crazy.damageCap(), 1.0E-6,
                "un tope negativo no es un tope: es cero, que es 'sin tope'");

        assertEquals(1.0F, crazy.armourPenetration(), 1.0E-6,
                "una penetracion de 400 % es 100 %: la armadura no puede ignorar mas que toda");

        assertTrue(crazy.bypassesArmour(),
                "y 100 % es lo que el tipo de dano de la armadura ignorada hace");
    }

    @Test
    @DisplayName("el default es exactamente el comportamiento de siempre")
    void theDefaultIsTheOldBehaviour() {
        assertEquals(0.0F, WarhammerTuning.DEFAULT.damageCap(), 1.0E-6,
                "el martillo no tenia tope");

        assertEquals(1.0F, WarhammerTuning.DEFAULT.armourPenetration(), 1.0E-6,
                "y el golpe cargado ignoraba la armadura entera");
    }
}
